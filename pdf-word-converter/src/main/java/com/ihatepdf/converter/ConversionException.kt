package com.ihatepdf.converter

sealed class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidInput(message: String) : ConversionException(message)
    class LimitExceeded(message: String) : ConversionException(message)
    class Unsupported(message: String) : ConversionException(message)
    class Io(message: String, cause: Throwable? = null) : ConversionException(message, cause)
}
