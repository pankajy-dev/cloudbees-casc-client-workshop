package com.cloudbees.opscenter.client.casc;

import org.hamcrest.Matcher;
import org.hamcrest.core.IsIterableContaining;

import static org.hamcrest.CoreMatchers.startsWith;

public interface CasCMatchers {
    static <T> Matcher<Iterable<? super String>> hasInfoMessage() {
        return new IsIterableContaining<>(startsWith("INFO"));
    }
}
