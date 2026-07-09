package com.anchat.pojo;

import lombok.Data;

@Data
public  class Result<T> {
    boolean success = false;
    String message;
    T object;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T o) {
        Result<T> result = new Result<>();
        result.success = true;
        result.object = o;
        return result;
    }

    public static <T> Result<T> fail() {
        Result<T> result = new Result<>();
        result.success = false;
        return result;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.success = false;
        result.message = message;
        return result;
    }
}
