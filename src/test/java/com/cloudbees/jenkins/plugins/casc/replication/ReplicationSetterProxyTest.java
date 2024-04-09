package com.cloudbees.jenkins.plugins.casc.replication;

import hudson.ExtensionList;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ReplicationSetterProxyTest {
    /**
     * A class that is not serializable
     */
    static class NotSerializableClass {
    }

    private static final NotSerializableClass notSerializableClass = new NotSerializableClass();

    /**
     * This interface will be proxied.
     * It contains:
     * - 'testXYZ' methods that should be replicated
     * - 'doNotReplicateXYZ' methods that should not be replicated
     * <p>
     * It also includes
     * - Boxed and Unboxed types
     * - non-serializable parameter
     * - (Spring) NonNull parameter
     */
    @SuppressWarnings("ALL") // contains invalid naming, unused methods, ...
    interface Target {
        void test();

        void doNotReplicate();

        void testByte(Byte value);

        void doNotReplicateByte(Byte value);

        void testbyte(byte value);

        void doNotReplicatebyte(byte value);

        void testShort(Short value);

        void doNotReplicateShort(Short value);

        void testshort(short value);

        void doNotReplicateshort(short value);

        void testInteger(Integer value);

        void doNotReplicateInteger(Integer value);

        void testint(int value);

        void doNotReplicateint(int value);

        void testLong(Long value);

        void doNotReplicateLong(Long value);

        void testlong(long value);

        void doNotReplicatelong(long value);

        void testFloat(Float value);

        void doNotReplicateFloat(Float value);

        void testfloat(float value);

        void doNotReplicatefloat(float value);

        void testDouble(Double value);

        void doNotReplicateDouble(Double value);

        void testdouble(double value);

        void doNotReplicatedouble(double value);

        void testCharacter(Character value);

        void doNotReplicateCharacter(Character value);

        void testchar(char value);

        void doNotReplicatechar(char value);

        void testBoolean(Boolean value);

        void doNotReplicateBoolean(Boolean value);

        void testboolean(boolean value);

        void doNotReplicateboolean(boolean value);

        void testString(String value);

        void testNonNullString(@org.springframework.lang.NonNull String value);

        void testNonNullStringString(@org.springframework.lang.NonNull String value, String other);

        void doNotReplicateString(String value);

        void testNotSerializable(NotSerializableClass value);

        void doNotReplicateNotSerializable(NotSerializableClass value);
    }

    /**
     * @see TestThrowImpl
     */
    interface TestThrow {
        void test();
    }

    /**
     * For some reason:
     * <code>
     * Target target = mock(Target.class);
     * doThrow(new NullPointerException()).when(target).test();
     * </code>
     * Cause the exception to be wrapped in an IllegalArgumentException when {@link ReplicationSetterProxy} invoke it.
     * The implementation of {@link ReplicationSetterProxy} rely on the documentation (https://docs.oracle.com/javase/tutorial/reflect/member/methodInvocation.html)
     * which indicate that "If the underlying method throws an exception, it will be wrapped by a java.lang.reflect.InvocationTargetException."
     *
     * This class achieves the same behavior (the target throws an exception) without using mock. This way, an
     * InvocationTargetException is thrown (instead of an IllegalArgumentException) and {@link ReplicationSetterProxy} implementation can rely on the documentation.
     */
    static class TestThrowImpl implements TestThrow {
        public void test() {
            throw new NullPointerException();
        }
    }

    @Test
    @Issue("BEE-46234")
    public void testShouldCallMethodOnTargetAndPublish() {
        Target target = mock(Target.class);
        String uuid = "Test UUID";
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, (m) -> m.getName().startsWith("test")) {
            @Override
            protected String getUUID() {
                return uuid;
            }
        };
        Target proxyInstance = (Target) Proxy.newProxyInstance(Target.class.getClassLoader(), new Class[]{Target.class}, proxy);

        try (MockedStatic<ExtensionList> extensionListStaticMock = mockStatic(ExtensionList.class)) {
            CasCStateChangePublisher replicationPublisher = mock(CasCStateChangePublisher.class);
            ExtensionList<CasCStateChangePublisher> extensionList = mock(ExtensionList.class);
            when(extensionList.isEmpty()).thenReturn(false);
            when(extensionList.iterator()).thenAnswer((invocation) -> Stream.of(replicationPublisher).iterator());
            extensionListStaticMock.when(() -> ExtensionList.lookup(CasCStateChangePublisher.class)).thenReturn(extensionList);

            // When testXXX method is called on the proxy
            proxyInstance.test();
            proxyInstance.testByte((byte) 0);
            proxyInstance.testbyte((byte) 0);
            proxyInstance.testShort((short) 0);
            proxyInstance.testshort((short) 0);
            proxyInstance.testInteger(0);
            proxyInstance.testint(0);
            proxyInstance.testLong(0L);
            proxyInstance.testlong(0L);
            proxyInstance.testFloat(0f);
            proxyInstance.testfloat(0f);
            proxyInstance.testDouble(0d);
            proxyInstance.testdouble(0d);
            proxyInstance.testCharacter('0');
            proxyInstance.testchar('0');
            proxyInstance.testBoolean(true);
            proxyInstance.testboolean(true);
            proxyInstance.testString("value");
            proxyInstance.testString(null);
            proxyInstance.testNotSerializable(notSerializableClass);

            // Then the method should be called on the target
            verifyTarget_testXYZ_MethodWasCalled(target);

            // Then, if the filter allows it and all parameters are serializable, it should be replicated
            // Allowed by the filter and serializable
            verify(replicationPublisher).publishStateChange(uuid, "test", null);
            verify(replicationPublisher).publishStateChange(uuid, "testByte", new Serializable[]{(byte) 0});
            verify(replicationPublisher).publishStateChange(uuid, "testbyte", new Serializable[]{(byte) 0});
            verify(replicationPublisher).publishStateChange(uuid, "testShort", new Serializable[]{(short) 0});
            verify(replicationPublisher).publishStateChange(uuid, "testshort", new Serializable[]{(short) 0});
            verify(replicationPublisher).publishStateChange(uuid, "testInteger", new Serializable[]{0});
            verify(replicationPublisher).publishStateChange(uuid, "testint", new Serializable[]{0});
            verify(replicationPublisher).publishStateChange(uuid, "testLong", new Serializable[]{0L});
            verify(replicationPublisher).publishStateChange(uuid, "testlong", new Serializable[]{0L});
            verify(replicationPublisher).publishStateChange(uuid, "testFloat", new Serializable[]{0f});
            verify(replicationPublisher).publishStateChange(uuid, "testfloat", new Serializable[]{0f});
            verify(replicationPublisher).publishStateChange(uuid, "testDouble", new Serializable[]{0d});
            verify(replicationPublisher).publishStateChange(uuid, "testdouble", new Serializable[]{0d});
            verify(replicationPublisher).publishStateChange(uuid, "testCharacter", new Serializable[]{'0'});
            verify(replicationPublisher).publishStateChange(uuid, "testchar", new Serializable[]{'0'});
            verify(replicationPublisher).publishStateChange(uuid, "testBoolean", new Serializable[]{true});
            verify(replicationPublisher).publishStateChange(uuid, "testboolean", new Serializable[]{true});
            verify(replicationPublisher).publishStateChange(uuid, "testString", new Serializable[]{"value"});
            verify(replicationPublisher).publishStateChange(uuid, "testString", new Serializable[]{null});

            // Allowed by the filter but not Serializable
            verify(replicationPublisher, never()).publishStateChange(eq(uuid), eq("testNotSerializable"), any());

            // When doNotReplicateXXX method is called on the proxy
            proxyInstance.doNotReplicate();
            proxyInstance.doNotReplicateByte((byte) 0);
            proxyInstance.doNotReplicatebyte((byte) 0);
            proxyInstance.doNotReplicateShort((short) 0);
            proxyInstance.doNotReplicateshort((short) 0);
            proxyInstance.doNotReplicateInteger(0);
            proxyInstance.doNotReplicateint(0);
            proxyInstance.doNotReplicateLong(0L);
            proxyInstance.doNotReplicatelong(0L);
            proxyInstance.doNotReplicateFloat(0f);
            proxyInstance.doNotReplicatefloat(0f);
            proxyInstance.doNotReplicateDouble(0d);
            proxyInstance.doNotReplicatedouble(0d);
            proxyInstance.doNotReplicateCharacter('0');
            proxyInstance.doNotReplicatechar('0');
            proxyInstance.doNotReplicateBoolean(true);
            proxyInstance.doNotReplicateboolean(true);
            proxyInstance.doNotReplicateString("value");
            proxyInstance.doNotReplicateNotSerializable(notSerializableClass);

            // Then the method should be called on the target (even if ignored for the listener)
            verifyTarget_doNotReplicateXYZ_MethodWasCalled(target);

            // Then, if the filter allows it and all parameters are serializable, it should be replicated
            // The filter doesn't include 'doNotReplicateXXX' so no more interaction should have occurred
            Mockito.verifyNoMoreInteractions(replicationPublisher);

            // Also, apart from the already verified methods call, nothing else should be called on the target
            Mockito.verifyNoMoreInteractions(target);
        }
    }

    @Test
    @Issue("BEE-46234")
    public void testShouldNotPublishIfTheTargetThrow() {
        // See documentation of TestThrowImpl
        TestThrow target = new TestThrowImpl();
        String uuid = "Test UUID";

        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return uuid;
            }
        };
        TestThrow proxyInstance = (TestThrow) Proxy.newProxyInstance(Target.class.getClassLoader(), new Class[]{TestThrow.class}, proxy);
        try (MockedStatic<ExtensionList> extensionListStaticMock = mockStatic(ExtensionList.class)) {
            CasCStateChangePublisher replicationPublisher = mock(CasCStateChangePublisher.class);
            ExtensionList<CasCStateChangePublisher> extensionList = mock(ExtensionList.class);
            when(extensionList.isEmpty()).thenReturn(false);
            when(extensionList.iterator()).thenAnswer((invocation) -> Stream.of(replicationPublisher).iterator());
            extensionListStaticMock.when(() -> ExtensionList.lookup(CasCStateChangePublisher.class)).thenReturn(extensionList);

            try {
                // When the target class throw an exception
                proxyInstance.test();
            } catch (NullPointerException e) {
                // expected
            }

            // Then no event should be published
            verifyNoInteractions(replicationPublisher);
        }

    }

    @Test
    @Issue("BEE-46234")
    public void testNullFilterShouldAcceptAll() {
        Target target = mock(Target.class);
        String uuid = "Test UUID";

        // When the filter is null
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return uuid;
            }
        };
        Target proxyInstance = (Target) Proxy.newProxyInstance(Target.class.getClassLoader(), new Class[]{Target.class}, proxy);
        try (MockedStatic<ExtensionList> extensionListStaticMock = mockStatic(ExtensionList.class)) {
            CasCStateChangePublisher replicationPublisher = mock(CasCStateChangePublisher.class);
            ExtensionList<CasCStateChangePublisher> extensionList = mock(ExtensionList.class);
            when(extensionList.isEmpty()).thenReturn(false);
            when(extensionList.iterator()).thenAnswer((invocation) -> Stream.of(replicationPublisher).iterator());
            extensionListStaticMock.when(() -> ExtensionList.lookup(CasCStateChangePublisher.class)).thenReturn(extensionList);

            // And a method is called
            proxyInstance.test();
            proxyInstance.doNotReplicate();

            // Then all calls should be replicated
            verify(replicationPublisher).publishStateChange(eq(uuid), eq("test"), any());
            verify(replicationPublisher).publishStateChange(eq(uuid), eq("doNotReplicate"), any());
        }
    }

    @Test
    @Issue("BEE-46234")
    void shouldFindMethodWhenArgumentAndUUIDMatches() {
        Target target = mock(Target.class);
        String uuid = "Test UUID";
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return uuid;
            }
        };

        // When the proxy receive an event from Jenkins
        proxy.onStateChange(uuid, "test", new Object[]{});
        proxy.onStateChange(uuid, "testByte", new Object[]{((byte) 0)});
        proxy.onStateChange(uuid, "testbyte", new Object[]{((byte) 0)});
        proxy.onStateChange(uuid, "testShort", new Object[]{((short) 0)});
        proxy.onStateChange(uuid, "testshort", new Object[]{((short) 0)});
        proxy.onStateChange(uuid, "testInteger", new Object[]{(0)});
        proxy.onStateChange(uuid, "testint", new Object[]{(0)});
        proxy.onStateChange(uuid, "testLong", new Object[]{(0L)});
        proxy.onStateChange(uuid, "testlong", new Object[]{(0L)});
        proxy.onStateChange(uuid, "testFloat", new Object[]{(0f)});
        proxy.onStateChange(uuid, "testfloat", new Object[]{(0f)});
        proxy.onStateChange(uuid, "testDouble", new Object[]{(0d)});
        proxy.onStateChange(uuid, "testdouble", new Object[]{(0d)});
        proxy.onStateChange(uuid, "testCharacter", new Object[]{('0')});
        proxy.onStateChange(uuid, "testchar", new Object[]{('0')});
        proxy.onStateChange(uuid, "testBoolean", new Object[]{(true)});
        proxy.onStateChange(uuid, "testboolean", new Object[]{(true)});
        proxy.onStateChange(uuid, "testString", new Object[]{("value")});
        proxy.onStateChange(uuid, "testString", new Object[]{(null)});
        proxy.onStateChange(uuid, "testNotSerializable", new Object[]{notSerializableClass});

        // It should call the corresponding method on the target
        verifyTarget_testXYZ_MethodWasCalled(target);
    }

    @Test
    @Issue("BEE-46234")
    void testValueChangedWithNull() {
        Target target = mock(Target.class);
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return "Test UUID";
            }
        };
        proxy.onStateChange("Test UUID", "test", null);
        verify(target).test();
    }


    @Test
    @Issue("BEE-46234")
    void shouldNotCallMethodIfNoUUIDMatches() {
        Target target = mock(Target.class);
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return "Another UUID";
            }
        };

        proxy.onStateChange("Test UUID", "test", new Object[]{});
        proxy.onStateChange("Test UUID", "testByte", new Object[]{((byte) 0)});
        proxy.onStateChange("Test UUID", "testbyte", new Object[]{((byte) 0)});
        proxy.onStateChange("Test UUID", "testShort", new Object[]{((short) 0)});
        proxy.onStateChange("Test UUID", "testshort", new Object[]{((short) 0)});
        proxy.onStateChange("Test UUID", "testInteger", new Object[]{(0)});
        proxy.onStateChange("Test UUID", "testint", new Object[]{(0)});
        proxy.onStateChange("Test UUID", "testLong", new Object[]{(0L)});
        proxy.onStateChange("Test UUID", "testlong", new Object[]{(0L)});
        proxy.onStateChange("Test UUID", "testFloat", new Object[]{(0f)});
        proxy.onStateChange("Test UUID", "testfloat", new Object[]{(0f)});
        proxy.onStateChange("Test UUID", "testDouble", new Object[]{(0d)});
        proxy.onStateChange("Test UUID", "testdouble", new Object[]{(0d)});
        proxy.onStateChange("Test UUID", "testCharacter", new Object[]{('0')});
        proxy.onStateChange("Test UUID", "testchar", new Object[]{('0')});
        proxy.onStateChange("Test UUID", "testBoolean", new Object[]{(true)});
        proxy.onStateChange("Test UUID", "testboolean", new Object[]{(true)});
        proxy.onStateChange("Test UUID", "testString", new Object[]{("value")});
        proxy.onStateChange("Test UUID", "testString", new Object[]{(null)});
        proxy.onStateChange("Test UUID", "testNotSerializable", new Object[]{notSerializableClass});

        // No interaction because the listener trigger was on another UUID
        verifyNoInteractions(target);
    }

    @Test
    @Issue("BEE-46234")
    void shouldNotCallMethodIfNoParametersMatches() {
        Target target = mock(Target.class);
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return "Test UUID";
            }
        };

        proxy.onStateChange("Test UUID", "test", new Object[]{""});
        proxy.onStateChange("Test UUID", "testByte", new Byte[]{(byte) 0, (byte) 0});
        proxy.onStateChange("Test UUID", "testbyte", new Byte[]{(byte) 0, (byte) 0});
        proxy.onStateChange("Test UUID", "testShort", new Object[]{(short) 0, (short) 0});
        proxy.onStateChange("Test UUID", "testshort", new Object[]{(short) 0, (short) 0});
        proxy.onStateChange("Test UUID", "testInteger", new Object[]{0, 0});
        proxy.onStateChange("Test UUID", "testint", new Object[]{0, 0});
        proxy.onStateChange("Test UUID", "testLong", new Object[]{0L, 0L});
        proxy.onStateChange("Test UUID", "testlong", new Object[]{0L, 0L});
        proxy.onStateChange("Test UUID", "testFloat", new Object[]{0f, 0f});
        proxy.onStateChange("Test UUID", "testfloat", new Object[]{0f, 0f});
        proxy.onStateChange("Test UUID", "testDouble", new Object[]{0d, 0d});
        proxy.onStateChange("Test UUID", "testdouble", new Object[]{0d, 0d});
        proxy.onStateChange("Test UUID", "testCharacter", new Object[]{'0', '0'});
        proxy.onStateChange("Test UUID", "testchar", new Object[]{'0', '0'});
        proxy.onStateChange("Test UUID", "testBoolean", new Object[]{true, true});
        proxy.onStateChange("Test UUID", "testboolean", new Object[]{true, true});
        proxy.onStateChange("Test UUID", "testString", new Object[]{"value", "value"});
        proxy.onStateChange("Test UUID", "testString", new Object[]{null, null});
        proxy.onStateChange("Test UUID", "testNotSerializable", new Object[]{notSerializableClass, notSerializableClass});

        // No interaction because the parameter count does not match
        verifyNoInteractions(target);
    }

    @Test
    @Issue("BEE-46234")
    void shouldNotCallTargetWithNullIfAnnotatedWithNonNull() {
        Target target = mock(Target.class);
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return "Test UUID";
            }
        };

        proxy.onStateChange("Test UUID", "testNonNullString", new Object[]{null});
        proxy.onStateChange("Test UUID", "testNonNullStringString", new Object[]{null, "other"});
        // No interaction because at least one of the String cannot be null
        verifyNoInteractions(target);
    }

    @Test
    @Issue("BEE-46234")
    void shouldNotCallTargetIfArgumentTypeMismatch() {
        Target target = mock(Target.class);
        ReplicationSetterProxy proxy = new ReplicationSetterProxy(target, null) {
            @Override
            protected String getUUID() {
                return "Test UUID";
            }
        };

        proxy.onStateChange("Test UUID", "testString", new Object[]{0});
        // No interaction because 0 is not a String
        verifyNoInteractions(target);
    }

    /**
     * Verify that every testXYZ methods were called once on the given target
     * @param target the target to verify
     */
    private static void verifyTarget_testXYZ_MethodWasCalled(Target target) {
        verify(target).test();
        verify(target).testByte((byte) 0);
        verify(target).testbyte((byte) 0);
        verify(target).testShort((short) 0);
        verify(target).testshort((short) 0);
        verify(target).testInteger(0);
        verify(target).testint(0);
        verify(target).testLong(0L);
        verify(target).testlong(0L);
        verify(target).testFloat(0f);
        verify(target).testfloat(0f);
        verify(target).testDouble(0d);
        verify(target).testdouble(0d);
        verify(target).testCharacter('0');
        verify(target).testchar('0');
        verify(target).testBoolean(true);
        verify(target).testboolean(true);
        verify(target).testString("value");
        verify(target).testString(null);
        verify(target).testNotSerializable(ReplicationSetterProxyTest.notSerializableClass);
    }

    /**
     * Verify that every doNotReplicateXYZ methods were called once on the given target
     * @param target the target to verify
     */
    private static void verifyTarget_doNotReplicateXYZ_MethodWasCalled(Target target) {
        verify(target).doNotReplicate();
        verify(target).doNotReplicateByte((byte) 0);
        verify(target).doNotReplicatebyte((byte) 0);
        verify(target).doNotReplicateShort((short) 0);
        verify(target).doNotReplicateshort((short) 0);
        verify(target).doNotReplicateInteger(0);
        verify(target).doNotReplicateint(0);
        verify(target).doNotReplicateLong(0L);
        verify(target).doNotReplicatelong(0L);
        verify(target).doNotReplicateFloat(0f);
        verify(target).doNotReplicatefloat(0f);
        verify(target).doNotReplicateDouble(0d);
        verify(target).doNotReplicatedouble(0d);
        verify(target).doNotReplicateCharacter('0');
        verify(target).doNotReplicatechar('0');
        verify(target).doNotReplicateBoolean(true);
        verify(target).doNotReplicateboolean(true);
        verify(target).doNotReplicateString("value");
        verify(target).doNotReplicateNotSerializable(ReplicationSetterProxyTest.notSerializableClass);
    }
}