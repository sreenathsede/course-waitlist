package org.springframework.boot.context.properties.bind; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface DefaultValue { String[] value() default {}; }
