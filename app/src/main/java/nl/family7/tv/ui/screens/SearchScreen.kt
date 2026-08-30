package nl.family7.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.family7.tv.data.Family7CatalogRepository
import nl.family7.tv.data.ProgramItem
import nl.family7.tv.ui.components.TVButton
import nl.family7.tv.ui.components.TVProgramCard
import nl.family7.tv.ui.theme.Family7BlueDark
import nl.family7.tv.ui.theme.Family7Orange
import nl.family7.tv.ui.theme.TextPrimary
import nl.family7.tv.ui.theme.TextSecondary

@Composable
fun SearchScreen(
    catalogRepo: Family7CatalogRepository,
    onSelectProgram: (ProgramItem) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLetter by remember { mutableStateOf("All") }
    var searchResults by remember { mutableStateOf<List<ProgramItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val letters = listOf("All", "1,2,3", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")

    // Load initial A-Z catalog
    LaunchedEffect(selectedLetter) {
        isSearching = true
        val letterParam = if (selectedLetter == "1,2,3") "1" else selectedLetter
        val res = catalogRepo.getAZCatalog(letterParam)
        res.onSuccess {
            searchResults = it
            isSearching = false
        }.onFailure {
            isSearching = false
        }
    }

    fun performSearch(q: String) {
        if (q.isBlank()) return
        isSearching = true
        scope.launch {
            val res = catalogRepo.search(q)
            res.onSuccess {
                searchResults = it
                isSearching = false
            }.onFailure {
                isSearching = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Family7BlueDark)
            .padding(28.dp)
    ) {
        // Top Row: Back button & Search Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TVButton(
                text = "TERUG",
                onClick = onBack,
                isPrimary = false,
                leadingIcon = {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
            )

            Spacer(modifier = Modifier.width(20.dp))

            TVInputField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.length >= 2) {
                        performSearch(it)
                    }
                },
                placeholder = "Zoek op titel of thema...",
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Family7Orange)
                },
                onImeAction = { performSearch(searchQuery) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // A-Z Alphabet Filter Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(letters) { letter ->
                val isSelected = selectedLetter == letter
                TVButton(
                    text = letter,
                    onClick = {
                        selectedLetter = letter
                        searchQuery = ""
                    },
                    isPrimary = isSelected
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Results Grid
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Family7Orange)
            }
        } else if (searchResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Geen programma's gevonden.",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            Text(
                text = "${searchResults.size} programma's gevonden:",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { program ->
                    TVProgramCard(
                        item = program,
                        onClick = { onSelectProgram(program) }
                    )
                }
            }
        }
    }
}
