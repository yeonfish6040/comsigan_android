package com.yftech.comsigan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.data.ComciService
import com.yftech.comsigan.data.School
import kotlinx.coroutines.launch

/** 학교명으로 컴시간에 등록된 학교를 찾는다. */
@Composable
fun SchoolSearchDialog(onDismiss: () -> Unit, onSelect: (School) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<School>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    fun search() {
        if (query.trim().length < 2) return
        scope.launch {
            isSearching = true
            message = null
            try {
                results = ComciService.searchSchools(context, query)
                if (results.isEmpty()) message = "검색 결과가 없습니다."
            } catch (error: Exception) {
                results = emptyList()
                message = error.message ?: "검색에 실패했습니다."
            }
            isSearching = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("학교 찾기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("학교명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )

                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    when {
                        isSearching -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        message != null -> Text(
                            message!!,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn {
                            items(results) { school ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(school) }
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(school.name)
                                    Text(
                                        school.region,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "일과진행을 사용하는 학교만 검색됩니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { search() }) { Text("검색") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
