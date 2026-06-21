package com.lovapinto;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyEntity {
    String tableName() default "";
}