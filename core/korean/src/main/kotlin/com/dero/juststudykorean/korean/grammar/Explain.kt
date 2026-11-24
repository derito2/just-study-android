package com.dero.juststudykorean.korean.grammar

// F3 (FASE_11_PLAN PR 5): the explanation of a form after a mistake, by rules, from conjugate's ConjugationTrace.

/** Why the form is what it is, by the rules conjugate followed (no model): "가다 + -아서/어서 → 가서: 가 + 아/어 contracts → 가." */
fun explain(verb: String, ending: Ending, kind: WordKind?): String? {
    val (forms, trace) = conjugateTraced(verb, ending, kind) ?: return null
    val first = forms.firstOrNull() ?: return null
    return "$verb + ${ending.label} → $first" + if (trace.steps.isEmpty()) "." else ": " + trace.steps.joinToString(". ") + "."
}
