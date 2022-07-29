package com.saggitt.omega.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.OutlinedButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.FolderInfo
import com.google.accompanist.flowlayout.FlowRow
import com.saggitt.omega.compose.components.ListItemWithIcon
import com.saggitt.omega.gestures.GestureController

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FolderListDialog(
    folder: FolderInfo,
    openDialogCustom: MutableState<Boolean>
) {
    Dialog(
        onDismissRequest = { openDialogCustom.value = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FolderListDialogUI(
            folder = folder,
            openDialogCustom = openDialogCustom
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListDialogUI(
    folder: FolderInfo,
    openDialogCustom: MutableState<Boolean>
) {
    val context = LocalContext.current
    val prefs = Utilities.getOmegaPrefs(context)

    var radius = 16.dp
    if (prefs.customWindowCorner) {
        radius = prefs.windowCornerRadius.dp
    }
    val cornerRadius by remember { mutableStateOf(radius) }
    val colors = RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = Color.Gray
    )

    Card(
        shape = RoundedCornerShape(cornerRadius),
        modifier = Modifier.padding(8.dp),
        elevation = 8.dp,
        backgroundColor = MaterialTheme.colorScheme.background
    ) {
        Column {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                val gestures = GestureController.getGestureHandlers(context, true, true)
                val (selectedOption, onOptionSelected) = remember {
                    mutableStateOf(folder.swipeUpAction)
                }
                LazyColumn(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                ) {
                    itemsIndexed(gestures) { _, item ->
                        ListItemWithIcon(
                            title = item.displayName,
                            modifier = Modifier.clickable {
                                folder.setSwipeUpAction(context, item.javaClass.name.toString())
                                onOptionSelected(item.javaClass.name.toString())
                            },
                            endCheckbox = {
                                RadioButton(
                                    selected = (item.javaClass.name.toString() == selectedOption),
                                    onClick = {
                                        folder.setSwipeUpAction(
                                            context,
                                            item.javaClass.name.toString()
                                        )
                                        onOptionSelected(item.javaClass.name.toString())
                                    },
                                    colors = colors
                                )
                            },
                            verticalPadding = 2.dp
                        )
                    }
                }
            }

            //Button Rows
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.End)
            ) {
                OutlinedButton(
                    shape = RoundedCornerShape(cornerRadius),
                    onClick = {
                        openDialogCustom.value = false
                    },
                    modifier = Modifier.padding(start = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(0.15f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = stringResource(id = android.R.string.cancel),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                    )
                }
            }
        }
    }
}