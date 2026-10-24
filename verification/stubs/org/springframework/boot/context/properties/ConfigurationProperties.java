package org.springframework.boot.context.properties; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface ConfigurationProperties { String prefix() default ""; }
