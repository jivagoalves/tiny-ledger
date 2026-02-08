package com.example.tinyledger

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

val serial = AtomicLong()

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL
}

@Serializable
data class Transaction(
    val id: Long = serial.incrementAndGet(),
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val type: TransactionType,
)

private object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }
    override fun deserialize(decoder: Decoder): BigDecimal {
        return BigDecimal((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)
    }
}