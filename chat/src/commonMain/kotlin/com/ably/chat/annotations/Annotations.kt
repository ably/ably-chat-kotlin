package com.ably.chat.annotations

/**
 * This API is experimental in the Ably Chat SDK. There is a chance
 * that those declarations will be deprecated in the near future or the semantics of
 * their behavior may change in some way that may break some code
 */
@MustBeDocumented
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is experimental in the Ably Chat SDK. There is a chance " +
        "that those declarations will be deprecated in the near future or the semantics of " +
        "their behavior may change in some way that may break some code",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER,
)
public annotation class ExperimentalChatApi

/**
 * A marker annotations for DSLs.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
public annotation class ChatDsl
