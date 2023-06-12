package com.cloudbees.jenkins.plugins.casc.analytics;

import com.cloudbees.analytics.gatherer.Gathering;
import com.cloudbees.analytics.gatherer.ProductUtils;
import com.cloudbees.analytics.gatherer.Util;
import com.cloudbees.analytics.messages.AnalyticsEvent;
import com.cloudbees.analytics.messages.AnalyticsProperty;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Send events to Segment when validation errors happen after a new version is available. The events are grouped by {@link ValidationCode} and send the number of warnings and errors
 */
public class BundleValidationErrorGatherer {

    private final List<Event> toSend;

    public BundleValidationErrorGatherer(@NonNull List<Validation> validations) {
        toSend = new ArrayList<>();
        List<ValidationCode> codes = validations.stream().filter(val -> val.getLevel() != Validation.Level.INFO).map(validation -> validation.getValidationCode()).distinct().collect(Collectors.toList());
        for (ValidationCode code : codes) {
            List<Validation> byCode = validations.stream().filter(validation -> validation.getValidationCode() == code).collect(Collectors.toList());
            long errors = byCode.stream().filter(validation -> validation.getLevel() == Validation.Level.ERROR).count();
            long warnings = byCode.stream().filter(validation -> validation.getLevel() == Validation.Level.WARNING).count();
            toSend.add(new Event(code, warnings, errors));
        }
    }

    /**
     * Send the events
     */
    public void send() {
        for (Event e : toSend) {
            e.send();
        }
    }

    public static class Event implements Gathering {

        @AnalyticsEvent(summary = "This event is sent every time a new version of a bundle is validated and any warning or error is found.")
        static final String EVENT = ProductUtils.JENKINS_EVENT_PREFIX + "bundle validated";

        @AnalyticsProperty(event = EVENT, summary = "Validation code associated to the message.")
        static final String PROP_VALIDATION_CODE = ProductUtils.JENKINS_PROPERTY_PREFIX + "validation_code";

        @AnalyticsProperty(event = EVENT, summary = "Total number of warnings found.")
        static final String PROP_TOTAL_WARNINGS = ProductUtils.JENKINS_PROPERTY_PREFIX + "total_warnings";

        @AnalyticsProperty(event = EVENT, summary = "Total number of errors found.")
        static final String PROP_TOTAL_ERRORS = ProductUtils.JENKINS_PROPERTY_PREFIX + "total_errors";

        private final Map<String, Object> data;

        private Event(ValidationCode code, long warnings, long errors) {
            data = new HashMap<>();
            Util.addUniversalEventProperties(data);
            data.put(PROP_VALIDATION_CODE, code.code());
            data.put(PROP_TOTAL_WARNINGS, warnings);
            data.put(PROP_TOTAL_ERRORS, errors);
        }

        @NotNull
        @Override
        public Map<String, Object> getData() {
            return Collections.unmodifiableMap(data);
        }

        @NotNull
        @Override
        public String getEvent() {
            return EVENT;
        }
    }
}
