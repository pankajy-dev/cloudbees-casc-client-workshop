package com.cloudbees.jenkins.plugins.casc.replication;

import com.cloudbees.jenkins.plugins.casc.events.CasCStateChangeListener;
import com.cloudbees.jenkins.plugins.casc.events.CasCStateChangePublisher;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jenkins.util.Listeners;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is both an {@link InvocationHandler} and a {@link CasCStateChangeListener}
 */
public abstract class ReplicationSetterProxy implements InvocationHandler, CasCStateChangeListener {
    private static final Logger LOGGER = Logger.getLogger(ReplicationSetterProxy.class.getName());

    private static final Map<Class<?>, Class<?>> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put(Byte.class, byte.class);
        PRIMITIVES.put(Short.class, short.class);
        PRIMITIVES.put(Integer.class, int.class);
        PRIMITIVES.put(Long.class, long.class);
        PRIMITIVES.put(Float.class, float.class);
        PRIMITIVES.put(Double.class, double.class);
        PRIMITIVES.put(Character.class, char.class);
        PRIMITIVES.put(Boolean.class, boolean.class);
    }

    /**
     * Forward method calls to this target
     */
    private final Object target;

    /**
     * Save all methods of the target
     */
    private final Method[] targetClassMethods;

    /**
     * Whether the method call should be replicated among other replicas
     */
    private final Function<Method, Boolean> shouldReplicate;

    public ReplicationSetterProxy(@NonNull Object target, @Nullable Function<Method, Boolean> shouldReplicate) {
        this.target = Objects.requireNonNull(target);
        this.targetClassMethods = target.getClass().getMethods();
        this.shouldReplicate = shouldReplicate;
    }

    protected abstract String getUUID();

    /**
     * Forward the method call to the given target and publish an event if:
     * - calling this method and arguments on the target doesn't throw an exception
     * - the given filter allows it,
     * - all the parameters are Serializable
     *
     * Note that this method deals with boxed/unboxed types. Also, please note that if the target class declares both a
     * 'public void setInt(int i);' and a 'public void setInt(Integer i);' then the latter will be used.
     *
     * @param proxy the proxy instance that the method was invoked on. This is not used.
     *
     * @param method the {@code Method} instance corresponding to
     * the interface method invoked on the proxy instance.
     *               The method name will be used to trigger the event.
     *
     * @param args an array of objects containing the values of the
     * arguments passed in the method invocation on the proxy instance,
     * or {@code null} if interface method takes no arguments.
     *            This will be used to trigger the event.
     *
     * @return the value returned by invoking this method and arguments on the target
     * @throws Throwable if the target throws an exception when calling this method and arguments
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // Call the target method first
            // As this class is mainly dedicated to be used with `setter` methods, there is no point in ignoring an exception
            // and call this method on other replicas.
            Object result = method.invoke(target, args);

            if (shouldReplicate == null || shouldReplicate.apply(method)) {
                if (args == null || Arrays.stream(args).allMatch(arg -> arg == null || arg instanceof Serializable)) {
                    // create an array to avoid ClassCastException
                    final Serializable[] serializable;
                    if (args == null) {
                        serializable = null;
                    } else {
                        serializable = Arrays.copyOf(args, args.length, Serializable[].class);
                    }
                    Listeners.notify(
                            CasCStateChangePublisher.class,
                            true,
                            publisher -> publisher.publishStateChange(getUUID(), method.getName(), serializable)
                    );
                } else {
                    LOGGER.warning("Unable to replicate this method invocation.");
                }
            }

            return result;
        } catch (InvocationTargetException e) {
            // https://docs.oracle.com/javase/tutorial/reflect/member/methodInvocation.html
            // If the underlying method throws an exception, it will be wrapped by an java.lang.reflect.InvocationTargetException.
            // The method's original exception may be retrieved using the exception chaining mechanism's InvocationTargetException.getCause() method.
            throw e.getCause();
        } // other exceptions from Method#invoke are unlikely because, for example, the target instance will always be of the correct type
    }

    /**
     * If the given UUID correspond to this {@link ReplicationSetterProxy} UUID, it try to
     * forward the value change to the given target if a candidate method can be found.
     * Valid candidate methods must satisfy those rules:
     * - it as the same name as the property
     * - all parameters are assignable to the given parameter array
     *
     * @param uuid the value change occurred on an instance with this UUID
     * @param property the value change occurred on this property
     * @param values the value change parameters
     */
    @Override
    public void onStateChange(@Nullable String uuid, @NonNull String property, @Nullable Object[] values) {
        if (!getUUID().equals(uuid)) {
            // This event is not for this ReplicationSetterProxy
            return;
        }
        int providedParameterCount = values != null ? values.length : 0;
        // Look for a candidate
        Optional<Method> candidate = Arrays.stream(targetClassMethods).filter(m -> {
            if (!m.getName().equals(property)) {
                return false;
            }
            int parameterCount = m.getParameterCount();
            if (parameterCount != providedParameterCount) {
                return false;
            }
            // Same name and same parameter count
            // Now check if all parameters have the correct type
            Parameter[] parameters = m.getParameters();
            for (int i = 0; i < providedParameterCount; i++) {
                if (values[i] == null) {
                    // Note, the findbug annotation scope is not Runtime
                    // The spring one is, so it's worth testing it
                    if (parameters[i].getAnnotation(org.springframework.lang.NonNull.class) != null) {
                        return false;
                    }
                } else {
                    if (!(/*compatible type*/parameters[i].getType().isAssignableFrom(values[i].getClass()) //
                            || /*compatible primitive*/parameters[i].getType().isAssignableFrom(PRIMITIVES.get(values[i].getClass()))
                    )) {
                        return false;
                    }
                }
            }
            return true;
        }).findAny();

        if (candidate.isEmpty()) {
            // This is very unlikely but possible.
            // Under normal circumstances, all replicas will use the same version of the plugin.
            // But it is possible that some replicas are not reloaded/restarted yet and use an older version of this plugin.
            // This state should not last for too long, all the replicas will be reloaded/restarted (the rolling restart can take time)
            LOGGER.log(Level.FINE, "Failure when trying to replicate the state of CasC to other replicas, no candidate found");
            return;
        }
        try {
            candidate.get().invoke(target, values);
        } catch (IllegalArgumentException e) {
            // Same as above, if no candidate is found.
            // This is very unlikely because the target instance will always have the correct type unless a reload/restart is pending.
            // Log a Fine message as this can help development.
            LOGGER.log(Level.FINE, "Failure when trying to replicate the state of CasC to other replicas", e);
        } catch (IllegalAccessException e) {
            // This Method object is enforcing Java language access control and the underlying method is inaccessible.
            // Log a warning as the replication will never be performed.
            LOGGER.log(Level.WARNING, "Failure when trying to replicate the state of CasC to other replicas");
        } catch (ExceptionInInitializerError | InvocationTargetException e) {
            // This is very unlikely because if the "original" method (on the other replica) thrown an exception, the event will not have been triggered.
            // This is possible if a reload/restart is pending and the previous version of the plugin throws an exception while the new one doesn't.
            // In this scenario, log the error as this can help development and do nothing else as the problem will be fixed after the reload/restart
            LOGGER.log(Level.FINE, "Failure when trying to replicate the state of CasC to other replicas, an exception was thrown");
        }
    }
}
