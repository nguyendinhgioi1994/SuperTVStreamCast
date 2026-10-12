package com.tuntech.supertvstreamcast.ui.onboarding

/** One page of the onboarding pager: a content step (0-based) or a full-page native ad. */
sealed interface OnboardingPage {
    data class Content(val step: Int) : OnboardingPage
    data class Ad(val name: String) : OnboardingPage
}

/**
 * The [steps] content pages with each ad inserted after `position` of them. The position is
 * clamped to 1..[steps], so the first page is always content and a larger value means the end.
 */
fun onboardingPages(steps: Int, ads: List<Pair<String, Int>>): List<OnboardingPage> = buildList {
    for (step in 0 until steps) {
        add(OnboardingPage.Content(step))
        ads.filter { (_, position) -> position.coerceIn(1, steps) == step + 1 }.forEach { (name, _) -> add(OnboardingPage.Ad(name)) }
    }
}

/** The content step shown in the header on [page]: an ad page keeps the step before it. */
fun List<OnboardingPage>.stepAt(page: Int): Int =
    (page downTo 0).firstNotNullOfOrNull { (getOrNull(it) as? OnboardingPage.Content)?.step } ?: 0
