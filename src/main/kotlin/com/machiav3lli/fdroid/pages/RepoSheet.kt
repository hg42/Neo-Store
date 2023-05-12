package com.machiav3lli.fdroid.pages

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.machiav3lli.fdroid.MainApplication
import com.machiav3lli.fdroid.R
import com.machiav3lli.fdroid.database.entity.Repository
import com.machiav3lli.fdroid.ui.activities.PrefsActivityX
import com.machiav3lli.fdroid.ui.components.ActionButton
import com.machiav3lli.fdroid.ui.components.BlockText
import com.machiav3lli.fdroid.ui.components.SelectChip
import com.machiav3lli.fdroid.ui.components.TitleText
import com.machiav3lli.fdroid.ui.compose.icons.Phosphor
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.Check
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.GearSix
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.TrashSimple
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.X
import com.machiav3lli.fdroid.ui.compose.utils.blockBorder
import com.machiav3lli.fdroid.ui.dialog.ActionsDialogUI
import com.machiav3lli.fdroid.ui.dialog.BaseDialog
import com.machiav3lli.fdroid.ui.dialog.DIALOG_NONE
import com.machiav3lli.fdroid.ui.dialog.StringInputDialogUI
import com.machiav3lli.fdroid.utility.extension.text.nullIfEmpty
import com.machiav3lli.fdroid.utility.extension.text.pathCropped
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URL
import java.util.Date
import java.util.Locale

const val DIALOG_ADDRESS = 1
const val DIALOG_FINGERPRINT = 2
const val DIALOG_USERNAME = 3
const val DIALOG_PASSWORD = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPage(
    repositoryId: Long,
    initEditMode: Boolean,
    onDismiss: () -> Unit,
    updateRepo: (Repository?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo by MainApplication.db.repositoryDao.getFlow(repositoryId)
        .collectAsState(initial = null)
    val appsCount by MainApplication.db.productDao.countForRepositoryFlow(repositoryId)
        .collectAsState(0)
    var editMode by remember { mutableStateOf(initEditMode) }
    val openDeleteDialog = remember { mutableStateOf(false) }
    val openDialog = remember { mutableStateOf(false) }
    val dialogProps = remember {
        mutableStateOf(DIALOG_NONE)
    }

    var addressFieldValue by remember(repo) {
        mutableStateOf(repo?.address.orEmpty())
    }
    var fingerprintFieldValue by remember(repo) {
        mutableStateOf(repo?.fingerprint.orEmpty())
    }
    var usernameFieldValue by remember(repo) {
        mutableStateOf(repo?.authenticationPair?.first.orEmpty())
    }
    var passwordFieldValue by remember(repo) {
        mutableStateOf(repo?.authenticationPair?.second.orEmpty())
    }

    val addressValidity = remember { mutableStateOf(false) }
    val fingerprintValidity = remember { mutableStateOf(false) }
    val usernameValidity = remember { mutableStateOf(false) }
    val passwordValidity = remember { mutableStateOf(false) }
    val validations =
        listOf(addressValidity, fingerprintValidity, usernameValidity, passwordValidity)

    SideEffect {
        if (editMode && repo?.address.isNullOrEmpty()) {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboardManager.primaryClip
                ?.let { if (it.itemCount > 0) it else null }
                ?.getItemAt(0)?.text?.toString().orEmpty()
            val (addressText, fingerprintText) = try {
                val uri = Uri.parse(URL(text.replaceFirst("fdroidrepos:", "https:")).toString())
                val fingerprintText =
                    uri.getQueryParameter("fingerprint")?.uppercase()?.nullIfEmpty()
                        ?: uri.getQueryParameter("FINGERPRINT")?.uppercase()?.nullIfEmpty()
                Pair(
                    uri.buildUpon().path(uri.path?.pathCropped)
                        .query(null).fragment(null).build().toString(), fingerprintText
                )
            } catch (e: Exception) {
                Pair(null, null)
            }
            if (addressText != null)
                addressFieldValue = addressText
            if (fingerprintText != null)
                fingerprintFieldValue = fingerprintText
        }

        invalidateAddress(addressValidity, addressFieldValue)
        invalidateFingerprint(fingerprintValidity, fingerprintFieldValue)
        invalidateAuthentication(
            passwordValidity,
            usernameFieldValue,
            passwordFieldValue,
        )
        invalidateAuthentication(
            usernameValidity,
            usernameFieldValue,
            passwordFieldValue,
        )
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(
                        id = if (!editMode) R.string.delete
                        else R.string.cancel
                    ),
                    icon = if (!editMode) Phosphor.TrashSimple
                    else Phosphor.X,
                    positive = false
                ) {
                    if (!editMode)
                        openDeleteDialog.value = true
                    else {
                        editMode = false
                        addressFieldValue = repo?.address.orEmpty()
                        fingerprintFieldValue = repo?.fingerprint.orEmpty()
                        usernameFieldValue = repo?.authenticationPair?.first.orEmpty()
                        passwordFieldValue = repo?.authenticationPair?.second.orEmpty()
                    }
                }
                ActionButton(
                    text = stringResource(
                        id = if (!editMode) R.string.edit
                        else R.string.save
                    ),
                    icon = if (!editMode) Phosphor.GearSix
                    else Phosphor.Check,
                    modifier = Modifier.weight(1f),
                    positive = true,
                    enabled = !editMode || validations.all { it.value },
                    onClick = {
                        if (!editMode) editMode = true
                        else {
                            // TODO show readable error
                            updateRepo(repo?.apply {
                                address = addressFieldValue
                                fingerprint = fingerprintFieldValue.uppercase()
                                setAuthentication(
                                    usernameFieldValue,
                                    passwordFieldValue,
                                )
                            })
                            // TODO sync a new when is already active
                            editMode = false
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    bottom = paddingValues.calculateBottomPadding(),
                    start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                )
                .blockBorder()
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            if ((repo?.updated ?: -1) > 0L && !editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.name),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(text = repo?.name)
                }
            }
            if (!editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.description),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(text = repo?.description?.replace("\n", " "))
                }
            }
            if ((repo?.updated ?: -1) > 0L && !editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.recently_updated),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(
                        text = if (repo != null && repo?.updated != null) {
                            val date = Date(repo?.updated ?: 0)
                            val format =
                                if (DateUtils.isToday(date.time)) DateUtils.FORMAT_SHOW_TIME else
                                    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
                            DateUtils.formatDateTime(context, date.time, format)
                        } else stringResource(R.string.unknown)
                    )
                }
            }
            if (!editMode && repo?.enabled == true &&
                (repo?.lastModified.orEmpty().isNotEmpty() ||
                        repo?.entityTag.orEmpty().isNotEmpty())
            ) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.number_of_applications),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(text = appsCount.toString())
                }
            }
            item {
                TitleText(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.address),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedVisibility(visible = !editMode) {
                    BlockText(text = repo?.address)
                }
                AnimatedVisibility(visible = editMode) {
                    Column {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            onClick = {
                                dialogProps.value = DIALOG_ADDRESS
                                openDialog.value = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = addressFieldValue)
                            }
                        }
                        if (repo?.mirrors?.isNotEmpty() == true) LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(repo?.mirrors ?: emptyList()) { text ->
                                SelectChip(
                                    text = text,
                                    checked = text == addressFieldValue,
                                ) {
                                    addressFieldValue = text
                                    invalidateAddress(addressValidity, addressFieldValue)
                                }
                            }
                        }
                    }
                }
            }
            item {
                TitleText(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.fingerprint),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedVisibility(visible = !editMode) {
                    BlockText(
                        text = if (
                            (repo?.updated ?: -1) > 0L
                            && repo?.fingerprint.isNullOrEmpty()
                        ) stringResource(id = R.string.repository_unsigned_DESC)
                        else repo?.fingerprint
                            ?.windowed(2, 2, false)
                            ?.joinToString(separator = " ") { it.uppercase(Locale.US) + " " },
                        color = if (
                            (repo?.updated ?: -1) > 0L
                            && repo?.fingerprint?.isEmpty() == true
                        ) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        monospace = true,
                    )
                }
                AnimatedVisibility(visible = editMode) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        onClick = {
                            dialogProps.value = DIALOG_FINGERPRINT
                            openDialog.value = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = fingerprintFieldValue
                                .windowed(2, 2, false)
                                .joinToString(separator = " ") { it.uppercase(Locale.US) + " " }
                            )
                        }
                    }
                }
            }
            if (editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.username),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        onClick = {
                            dialogProps.value = DIALOG_USERNAME
                            openDialog.value = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = usernameFieldValue)
                        }
                    }
                }
                item {
                    TitleText(
                        modifier = Modifier
                            .clickable {
                                dialogProps.value = DIALOG_PASSWORD
                                openDialog.value = true
                            }
                            .fillMaxWidth(),
                        text = stringResource(id = R.string.password),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        onClick = {
                            dialogProps.value = DIALOG_PASSWORD
                            openDialog.value = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = passwordFieldValue)
                        }
                    }
                }
            }
        }
    }

    if (openDeleteDialog.value) {
        BaseDialog(openDialogCustom = openDeleteDialog) {
            ActionsDialogUI(
                titleText = stringResource(id = R.string.confirmation),
                messageText = "${repo?.name}: ${stringResource(id = R.string.delete_repository_DESC)}",
                openDialogCustom = openDeleteDialog,
                primaryText = stringResource(id = R.string.delete),
                primaryIcon = Phosphor.TrashSimple,
                primaryAction = {
                    scope.launch {
                        (context as PrefsActivityX).syncConnection
                            .binder?.deleteRepository(repositoryId)
                        onDismiss()
                    }
                },
            )
        }
    }

    if (openDialog.value) BaseDialog(openDialogCustom = openDialog) {
        dialogProps.value.let { dialogMode ->
            when (dialogMode) {
                DIALOG_ADDRESS -> {
                    StringInputDialogUI(
                        titleText = stringResource(id = R.string.address),
                        initValue = repo?.address ?: "",
                        openDialogCustom = openDialog
                    ) {
                        addressFieldValue = it
                        invalidateAddress(addressValidity, addressFieldValue)
                    }
                }

                DIALOG_FINGERPRINT -> {
                    StringInputDialogUI(
                        titleText = stringResource(id = R.string.fingerprint),
                        initValue = repo?.fingerprint ?: "",
                        openDialogCustom = openDialog
                    ) {
                        fingerprintFieldValue = it
                        invalidateFingerprint(fingerprintValidity, fingerprintFieldValue)
                    }
                }

                DIALOG_USERNAME -> {
                    StringInputDialogUI(
                        titleText = stringResource(id = R.string.username),
                        initValue = repo?.authenticationPair?.first ?: "",
                        openDialogCustom = openDialog
                    ) {
                        usernameFieldValue = it
                        invalidateAuthentication(
                            passwordValidity,
                            usernameFieldValue,
                            passwordFieldValue,
                        )
                    }
                }

                DIALOG_PASSWORD -> {
                    StringInputDialogUI(
                        titleText = stringResource(id = R.string.password),
                        initValue = repo?.authenticationPair?.second ?: "",
                        openDialogCustom = openDialog
                    ) {
                        passwordFieldValue = it
                        invalidateAuthentication(
                            passwordValidity,
                            usernameFieldValue,
                            passwordFieldValue,
                        )
                    }
                }
            }
        }
    }
}


private fun invalidateAddress(
    validity: MutableState<Boolean>,
    address: String,
) {
    // TODO check if already used
    validity.value = normalizeAddress(address) != null
}


private fun invalidateFingerprint(validity: MutableState<Boolean>, fingerprint: String) {
    validity.value = fingerprint.isEmpty() || fingerprint.length == 64
}


private fun invalidateAuthentication(
    validity: MutableState<Boolean>,
    username: String,
    password: String,
) {
    val usernameInvalid = username.contains(':')
    val usernameEmpty = username.isEmpty() && password.isNotEmpty()
    val passwordEmpty = username.isNotEmpty() && password.isEmpty()
    validity.value = !(usernameInvalid || usernameEmpty || passwordEmpty)
}

private fun normalizeAddress(address: String): String? {
    val uri = try {
        val uri = URI(address)
        if (uri.isAbsolute) uri.normalize() else null
    } catch (e: Exception) {
        null
    }
    val path = uri?.path?.pathCropped
    return if (uri != null && path != null) {
        try {
            URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                path,
                uri.query,
                uri.fragment
            ).toString()
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }
}
