package me.masonasons.fastsm.domain.model

data class SearchResults(
    val users: List<UniversalUser> = emptyList(),
    val posts: List<UniversalStatus> = emptyList(),
    val hashtags: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = users.isEmpty() && posts.isEmpty() && hashtags.isEmpty()
}
