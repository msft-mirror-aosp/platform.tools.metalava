/*
 [The "BSD licence"]
 Copyright (c) 2016 Pascal Gruen
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

// $antlr-format alignTrailingComments true, columnLimit 150, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine true, allowShortBlocksOnASingleLine true, minEmptyLines 0, alignSemicolons ownLine
// $antlr-format alignColons trailing, singleLineOverrulesHangingColon true, alignLexerCommands true, alignLabels true, alignTrailers true

lexer grammar AntlrJavadocLexer;

@header {
package com.android.tools.metalava.model.source.javadoc;
}

// This document is split into sections one for each mode. It is important that rules are added in
// the correct section otherwise they will be in the wrong mode and not behave as expected. Also,
// order of rules matters when determining matches. Usually the longest match wins but if two
// rules match the same text then the rule listed first wins.

// ============================== BEGIN DEFAULT_MODE ==============================
// This is the default mode that will be used when the lexer first starts.

NEWLINE:
    '\n' (SPACE? '*'+)?
    | '\r\n' (SPACE? '*'+)?
    | '\r' (SPACE? '*'+)?
;

SPACE: (' ' | '\t')+;

BRACE_OPEN: '{';

BRACE_CLOSE: '}';

// General text content. Excludes characters that are handled by one of the other
// tokens above.
TEXT_CONTENT: ~[\n\r\t {}]+;

// The start of an inline tag.
INLINE_TAG_START: '{@' ->
    // Start a special mode for processing the INLINE_TAG_NAME. That avoids having to exclude
    // characters in the INLINE_TAG_NAME from TEXT_CONTENT.
    pushMode(INLINE_TAG_MODE);

// ============================== END DEFAULT_MODE ==============================

// ============================== BEGIN INLINE_TAG_MODE ==============================
// This mode is in use after `{@`. It switches to INLINE_TAG_CONTENT_MODE after seeing an
// INLINE_TAG_NAME.
mode INLINE_TAG_MODE;

// The inline tag name.
INLINE_TAG_NAME: [a-zA-Z]+ ->
    // Pop this mode to switch back to the default mode.
    popMode;

// ============================== END INLINE_TAG_MODE ==============================

// Add new modes before this line.
// ============================== END OF FILE ==============================
// Do not add any more rules below here as they will appear in whatever mode was created last.
