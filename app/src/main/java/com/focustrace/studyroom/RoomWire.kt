package com.focustrace.studyroom

import java.io.*

internal sealed interface RoomMessage {
    data class Hello(val profile: RoomProfile, val focus: RoomFocus) : RoomMessage
    data class Welcome(val name: String, val selfId: Int, val members: List<RoomMember>) : RoomMessage
    data class Joined(val member: RoomMember) : RoomMessage
    data class Left(val id: Int) : RoomMessage
    data class Update(val id: Int, val focus: RoomFocus) : RoomMessage
    data object Closed : RoomMessage
    data object Full : RoomMessage
}

internal object RoomWire {
    const val VERSION = 2
    const val CLIENT_FRAME_LIMIT = 36 * 1024
    private const val FRAME_LIMIT = 448 * 1024

    fun write(output: DataOutputStream, message: RoomMessage) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).apply {
            writeByte(VERSION)
            when (message) {
                is RoomMessage.Hello -> { writeByte(1); profile(message.profile); focus(message.focus) }
                is RoomMessage.Welcome -> {
                    writeByte(2); writeUTF(message.name); writeInt(message.selfId)
                    writeByte(message.members.size); message.members.forEach { member(it) }
                }
                is RoomMessage.Joined -> { writeByte(3); member(message.member) }
                is RoomMessage.Left -> { writeByte(4); writeInt(message.id) }
                is RoomMessage.Update -> { writeByte(5); writeInt(message.id); focus(message.focus) }
                RoomMessage.Closed -> writeByte(6)
                RoomMessage.Full -> writeByte(7)
            }
        }
        if (buffer.size() > FRAME_LIMIT) invalid()
        output.writeInt(buffer.size())
        buffer.writeTo(output)
        output.flush()
    }

    fun read(input: DataInputStream, limit: Int = FRAME_LIMIT): RoomMessage {
        val size = input.readInt()
        if (size !in 2..limit) invalid()
        val packet = DataInputStream(ByteArrayInputStream(ByteArray(size).also { input.readFully(it) }))
        return with(packet) {
            if (readUnsignedByte() != VERSION) throw IOException("不兼容的自习室版本")
            val message = when (readUnsignedByte()) {
                1 -> RoomMessage.Hello(profile(), focus())
                2 -> {
                    val name = text(63)
                    val self = id()
                    val count = readUnsignedByte().also { if (it !in 2..ROOM_CAPACITY) invalid() }
                    val members = List(count) { member() }
                    val ids = members.map(RoomMember::id).toSet()
                    if (self == HOST_ID || self !in ids || HOST_ID !in ids || ids.size != count) invalid()
                    RoomMessage.Welcome(name, self, members)
                }
                3 -> RoomMessage.Joined(member())
                4 -> RoomMessage.Left(id())
                5 -> RoomMessage.Update(id(), focus())
                6 -> RoomMessage.Closed
                7 -> RoomMessage.Full
                else -> invalid()
            }
            if (available() != 0) invalid()
            message
        }
    }

    private fun DataOutputStream.member(value: RoomMember) {
        writeInt(value.id); profile(value.profile); focus(value.focus)
    }
    private fun DataInputStream.member() = RoomMember(id(), profile(), focus())
    private fun DataInputStream.id(): Int = readInt().also { if (it <= 0) invalid() }
    private fun DataOutputStream.profile(value: RoomProfile) {
        writeUTF(value.nickname); writeUTF(value.avatar)
    }
    private fun DataInputStream.profile(): RoomProfile {
        val name = text(NICKNAME_LIMIT)
        val avatar = readUTF().also { if (it.length > AVATAR_LIMIT || it.any { c ->
                c !in 'A'..'Z' && c !in 'a'..'z' && c !in '0'..'9' && c != '+' && c != '/' && c != '='
            }) invalid() }
        return RoomProfile(name, avatar)
    }
    private fun DataOutputStream.focus(value: RoomFocus) {
        writeByte(value.status.code); writeUTF(value.task.orEmpty()); writeLong(value.seconds)
        writeBoolean(value.ticking); writeLong(value.limitSeconds); writeLong(value.todaySeconds)
    }
    private fun DataInputStream.focus(): RoomFocus {
        val code = readUnsignedByte()
        val status = RoomStatus.entries.firstOrNull { it.code == code } ?: invalid()
        val task = readUTF().also { if (it.length > TASK_TITLE_LIMIT) invalid() }.ifBlank { null }
        val seconds = readLong()
        val ticking = readBoolean()
        val limit = readLong()
        if (seconds !in 0..limit || (ticking && status != RoomStatus.FOCUSING)) invalid()
        val today = readLong().also { if (it < 0) invalid() }
        return RoomFocus(status, task, seconds, ticking, limit, today)
    }
    private fun DataInputStream.text(max: Int) = readUTF().also { if (it.isBlank() || it.length > max) invalid() }
    private fun invalid(): Nothing = throw IOException("无效的自习室消息")
}
