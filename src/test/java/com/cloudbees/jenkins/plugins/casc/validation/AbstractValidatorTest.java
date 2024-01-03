package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AbstractValidatorTest {

    @Test
    public void testLogValidation() {
        Logger loggerMock = mock(Logger.class);

        Validation info = Validation.info(ValidationCode.UNDEFINED, "info message");
        Validation warning = Validation.warning(ValidationCode.UNDEFINED, "warning message");
        Validation error = Validation.error(ValidationCode.UNDEFINED, "error message");
        List<Validation> validations = new ArrayList<>();
        validations.add(info);
        validations.add(warning);
        validations.add(error);

        AbstractValidator.logValidation(loggerMock, "Test %s", validations);

        verify(loggerMock, times(1)).log(Level.INFO, info.getMessage());

        ArgumentCaptor<String> warningCaptor = ArgumentCaptor.forClass(String.class);
        verify(loggerMock, times(1)).log(eq(Level.WARNING), warningCaptor.capture());

        String warningMessage = warningCaptor.getValue();
        assertThat("Should use the given template", warningMessage, startsWith("Test "));
        assertThat("Should contains the warning", warningMessage, containsString(warning.getMessage()));
        assertThat("Should contains the error", warningMessage, containsString(error.getMessage()));
    }
}