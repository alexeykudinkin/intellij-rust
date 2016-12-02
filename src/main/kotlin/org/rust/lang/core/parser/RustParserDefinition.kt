package org.rust.lang.core.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageUtil
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.RustFileElementType
import org.rust.lang.core.lexer.RustLexer
import org.rust.lang.core.psi.RustCompositeElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes.EOL_COMMENTS_TOKEN_SET
import org.rust.lang.core.psi.RustTokenElementTypes.STRING_LITERAL
import org.rust.lang.core.psi.impl.RustFile

class RustParserDefinition : ParserDefinition {

    override fun createFile(viewProvider: FileViewProvider): PsiFile? =
        RustFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements {
        if (left.elementType in EOL_COMMENTS_TOKEN_SET) return ParserDefinition.SpaceRequirements.MUST_LINE_BREAK
        return LanguageUtil.canStickTokensTogetherByLexer(left, right, RustLexer())
    }

    override fun getFileNodeType(): IFileElementType? = RustFileElementType

    override fun getStringLiteralElements(): TokenSet =
        TokenSet.create(STRING_LITERAL)

    override fun getWhitespaceTokens(): TokenSet =
        TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens() = RustTokenElementTypes.COMMENTS_TOKEN_SET

    override fun createElement(node: ASTNode?): PsiElement =
        RustCompositeElementTypes.Factory.createElement(node)

    override fun createLexer(project: Project?): Lexer = RustLexer()

    override fun createParser(project: Project?): PsiParser = RustParser()
}
