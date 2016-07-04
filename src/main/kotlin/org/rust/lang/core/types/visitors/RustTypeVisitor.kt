package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.*


/**
 * Resolved types visitor trait
 */
interface RustTypeVisitor<T> {

    fun visitStruct(type: RustStructType): T

    fun visitTupleType(type: RustTupleType): T

    fun visitUnknown(type: RustUnknownType): T

    fun visitUnitType(type: RustUnitType): T

    fun visitFunctionType(type: RustFunctionType): T

    fun visitEnum(type: RustEnumType): T

    fun visitInteger(type: RustIntegerType): T

    fun visitReference(type: RustReferenceType): T

    fun visitFloat(type: RustFloatType): T

    fun visitString(type: RustStringType): T

    fun visitChar(type: RustCharacterType): T

    fun visitBoolean(type: RustBooleanType): T

}

