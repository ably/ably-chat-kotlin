package com.ably.chat

/**
 * Headers are a flat key-value map that can be attached to chat messages.
 *
 * The headers are a flat key-value map and are sent as part of the realtime
 * message's extras inside the `headers` property. They can serve similar
 * purposes as Metadata but as opposed to Metadata they are read by Ably and
 * can be used for features such as
 * [subscription filters](https://faqs.ably.com/subscription-filters).
 *
 * Do not use the headers for authoritative information. There is no
 * server-side validation. When reading the headers treat them like user
 * input.
 *
 * The key prefix `ably-chat` is reserved and cannot be used. Ably may add
 * headers prefixed with `ably-chat` in the future.
 */
public typealias Headers = Map<String, String>
