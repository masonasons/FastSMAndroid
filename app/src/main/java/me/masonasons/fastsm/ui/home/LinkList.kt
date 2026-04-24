package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Compact visual list of URLs found in a post. Each row is tappable for
 * sighted users; TalkBack reads the parent post's "Open link: <host>" custom
 * actions instead, so the individual rows are hidden from a11y.
 */
@Composable
internal fun LinkList(
    links: List<String>,
    onOpenLink: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clearAndSetSemantics { },
    ) {
        links.forEach { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLink(url) },
            )
        }
    }
}
