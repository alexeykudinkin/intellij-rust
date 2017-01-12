package org.rust.lang.core.types

import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.RsType
import java.io.DataInput
import java.io.DataOutput

data class RustTypeFingerprint private constructor(
    private val name: String
) {
    companion object {
        fun create(type: RsType): RustTypeFingerprint? = when (type) {
            is RsBaseType -> type.path?.referenceName?.let(::RustTypeFingerprint)
            is RsRefLikeType -> type.type?.let { create(it) }
            else -> null
        }

        fun create(type: RustType): RustTypeFingerprint? = when (type) {
            is RustStructType -> type.item.name?.let(::RustTypeFingerprint)
            is RustEnumType -> type.item.name?.let(::RustTypeFingerprint)
            is RustReferenceType -> create(type.referenced)
            else -> null
        }
    }

    object KeyDescriptor : com.intellij.util.io.KeyDescriptor<RustTypeFingerprint> {
        override fun save(out: DataOutput, value: RustTypeFingerprint) =
            out.writeUTF(value.name)

        override fun read(`in`: DataInput): RustTypeFingerprint =
            RustTypeFingerprint(`in`.readUTF())

        override fun getHashCode(value: RustTypeFingerprint): Int = value.hashCode()

        override fun isEqual(lhs: RustTypeFingerprint, rhs: RustTypeFingerprint): Boolean = lhs == rhs
    }
}
