package org.rust.lang.core.types.visitors.impl

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.contains
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.types.*
import org.rust.lang.core.types.util.resolvedType
import org.rust.utils.Result


object RustTypeInferenceEngine {

    fun inferPatBindingTypeFrom(binding: RsPatBinding, pat: RsPat, type: RustType): RustType {
        check(pat.contains(binding))

        return run(pat, type).let {
            if (it is Result.Ok)
                it.result.getOrElse(binding, { error("Panic! Successful match should imbue all the bindings!") })
            else
                RustUnknownType
        }
    }

    private fun run(pat: RsPat, type: RustType): Result<Map<RsPatBinding, RustType>> =
        RustTypeInferencingVisitor(type).let {
            if (it.compute(pat)) Result.Ok(it.bindings) else Result.Failure
        }
}

@Suppress("IfNullToElvis")
private class RustTypeInferencingVisitor(var type: RustType) : RustComputingVisitor<Boolean>() {

    val bindings: MutableMap<RsPatBinding, RustType> = hashMapOf()

    private fun match(pat: RsPat, type: RustType): Boolean {
        val prev = this.type

        this.type = type
        try {
            return compute(pat)
        } finally {
            this.type = prev
        }
    }

    override fun visitElement(element: PsiElement?) {
        throw UnsupportedOperationException("Panic! Unhandled pattern detected!")
    }

    override fun visitPatIdent(o: RsPatIdent) = set(fun(): Boolean {
        val pat = o.pat
        if (pat == null || match(pat, type)) {
            bindings += o.patBinding to type
            return true
        }

        return false
    })

    override fun visitPatWild(o: RsPatWild) = set({ true })

    override fun visitPatTup(o: RsPatTup) = set(fun(): Boolean {
        val type = type
        if (type !is RustTupleType)
            return false

        val pats = o.patList
        if (pats.size != type.size)
            return false

        for (i in 0..type.size - 1) {
            if (!match(pats[i], type[i]))
                return false
        }

        return true
    })

    private fun getEnumByVariant(e: RsEnumVariant): RsEnumItem? =
        (e.parent as RsEnumBody).parent as? RsEnumItem

    override fun visitPatEnum(o: RsPatEnum) = set(fun(): Boolean {
        //
        // `pat_enum` perfectly covers 2 following destructuring scenarios:
        //
        //      > Named-tuple structs       [1]
        //      > Named-tuple enum-variants [2]
        //
        //
        //      ```
        //      // [1]
        //      struct S(i32);
        //
        //      // [2]
        //      enum E {
        //          X(i32)
        //      }
        //
        //      fn foo(x: E::X, s: S) {
        //          let E::X(i) = x     // Both of those `pat`s are `pat_enum`s
        //          let S(j) = s;       //
        //      }
        //      ```
        //

        val type = type

        val e = o.path.reference.resolve()
        val tupleFields = when (e) {
            is RsStructItem -> {
                if (type !is RustStructType || type.item !== e)
                    return false

                e.tupleFields?.tupleFieldDeclList
            }

            is RsEnumVariant -> {
                if (type !is RustEnumType || type.item !== getEnumByVariant(e))
                    return false

                e.tupleFields?.tupleFieldDeclList
            }

            else -> null
        } ?: return false

        if (tupleFields.size != o.patList.size)
            return false

        for (i in 0 until tupleFields.size) {
            if (!match(o.patList[i], tupleFields[i].type.resolvedType))
                return false
        }

        return true
    })

    override fun visitPatStruct(o: RsPatStruct) = set(fun(): Boolean {
        val type = type
        if (type !is RustStructType || type.item !== o.path.reference.resolve())
            return false

        val fieldDecls =
            type.item.blockFields?.let {
                it.fieldDeclList.map { it.name to it }.toMap()
            } ?: emptyMap()

        // `..` allows to match for the struct's fields not to be exhaustive
        if (o.patFieldList.size != fieldDecls.size && o.dotdot == null)
            return false

        for (patField in o.patFieldList) {
            val patBinding = patField.patBinding
            if (patBinding != null) {
                val fieldDecl = fieldDecls[patBinding.identifier.text]
                if (fieldDecl == null)
                    return false

                bindings += patBinding to fieldDecl.type.resolvedType
            } else {
                val id = patField.identifier
                if (id == null)
                    error("Panic! `pat_field` may be either `pat_binding` or should contain identifier!")

                val patFieldPat = patField.pat
                if (patFieldPat == null)
                    return false

                val fieldDecl = fieldDecls[id.text]
                if (fieldDecl == null || !match(patFieldPat, fieldDecl.type.resolvedType))
                    return false
            }
        }

        return true
    })

    override fun visitPatRef(o: RsPatRef) = set(fun(): Boolean {
        val type = type
        return type is RustReferenceType
            && type.mutable == (o.mut != null)
            && match(o.pat, type.referenced)
    })

    override fun visitPatUniq(o: RsPatUniq) = set(fun(): Boolean {
        // FIXME
        return false
    })

    override fun visitPatVec(o: RsPatVec) = set(fun(): Boolean {
        // FIXME
        return false
    })
}
