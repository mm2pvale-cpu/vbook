package com.kyant.backdrop.catalog

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LocalIsBackdropRow
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidSegmentedControl
import com.kyant.backdrop.catalog.components.LiquidGlassMenu
import com.kyant.backdrop.catalog.data.AppDatabase
import com.kyant.backdrop.catalog.data.Book
import com.kyant.backdrop.catalog.data.BookPage
import com.kyant.backdrop.catalog.data.BookViewModel
import com.kyant.backdrop.catalog.data.SavedPoint
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.pointer.pointerInput
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidBookSelectMenu
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration

@Composable
fun MainContent() {
    val context = LocalContext.current
    val viewModel: BookViewModel = viewModel()
    
    var destination by rememberSaveable(stateSaver = CatalogDestination.Saver) {
        mutableStateOf(CatalogDestination.Home)
    }

    val activeBookId by viewModel.activeBookId.collectAsState()

    BackHandler(destination != CatalogDestination.Home) {
        if (destination == CatalogDestination.Reader) {
            viewModel.setActiveBook(null)
        }
        destination = CatalogDestination.Home
    }

    // Capture standard backdrop effect setup from the custom glass kit
    val mainBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E12)) // Pure slate elegant layout backdrop
    ) {
        // Main view content container sits as a sibling to navigation to avoid any feedback cycles/crashes
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (destination) {
                CatalogDestination.Home -> LibraryGridContent(
                    viewModel = viewModel,
                    backdrop = mainBackdrop,
                    onReadBook = { bookId ->
                        viewModel.setActiveBook(bookId)
                        destination = CatalogDestination.Reader
                    }
                )
                CatalogDestination.Upload -> UploadContent(viewModel = viewModel, backdrop = mainBackdrop)
                CatalogDestination.Settings -> SettingsContent(viewModel = viewModel, backdrop = mainBackdrop)
                CatalogDestination.Reader -> {
                    activeBookId?.let { bookId ->
                        ReaderContent(
                            bookId = bookId,
                            viewModel = viewModel,
                            backdrop = mainBackdrop,
                            onClose = {
                                viewModel.setActiveBook(null)
                                destination = CatalogDestination.Home
                            }
                        )
                    } ?: run {
                        destination = CatalogDestination.Home
                    }
                }
            }
        }

        // Floating overlays containing Liquid bottom tabs sitting securely on top
        if (destination != CatalogDestination.Reader) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // respect system navigation bar offsets dynamically
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
            ) {
                val tabsCount = 3
                val activeTabIndex = when (destination) {
                    CatalogDestination.Home -> 0
                    CatalogDestination.Upload -> 1
                    CatalogDestination.Settings -> 2
                    else -> 0
                }

                LiquidBottomTabs(
                    selectedTabIndex = { activeTabIndex },
                    onTabSelected = { index ->
                        destination = when (index) {
                            0 -> CatalogDestination.Home
                            1 -> CatalogDestination.Upload
                            2 -> CatalogDestination.Settings
                            else -> CatalogDestination.Home
                        }
                    },
                    backdrop = mainBackdrop,
                    tabsCount = tabsCount,
                    modifier = Modifier
                        .width(320.dp)
                        .height(64.dp)
                ) {
                    // Home tab
                    LiquidBottomTab(
                        onClick = { destination = CatalogDestination.Home }
                    ) {
                        val isBackdrop = LocalIsBackdropRow.current
                        val tabSelected = activeTabIndex == 0
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "Library",
                            tint = if (isBackdrop && tabSelected) {
                                Color(0xFF0091FF) // Active liquid accent blue text
                            } else {
                                Color.White.copy(alpha = if (tabSelected) 1f else 0.5f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        // Selected tab text fades out when active background row selected to completely avoid blurry overlapping
                        val textAlpha = if (!isBackdrop && tabSelected) 0f else 1f
                        Text(
                            text = "Library",
                            color = if (isBackdrop && tabSelected) Color(0xFF0091FF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (tabSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.alpha(textAlpha)
                        )
                    }

                    // Upload tab
                    LiquidBottomTab(
                        onClick = { destination = CatalogDestination.Upload }
                    ) {
                        val isBackdrop = LocalIsBackdropRow.current
                        val tabSelected = activeTabIndex == 1
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Upload",
                            tint = if (isBackdrop && tabSelected) {
                                Color(0xFF0091FF)
                            } else {
                                Color.White.copy(alpha = if (tabSelected) 1f else 0.5f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        val textAlpha = if (!isBackdrop && tabSelected) 0f else 1f
                        Text(
                            text = "Upload",
                            color = if (isBackdrop && tabSelected) Color(0xFF0091FF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (tabSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.alpha(textAlpha)
                        )
                    }

                    // Settings tab
                    LiquidBottomTab(
                        onClick = { destination = CatalogDestination.Settings }
                    ) {
                        val isBackdrop = LocalIsBackdropRow.current
                        val tabSelected = activeTabIndex == 2
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isBackdrop && tabSelected) {
                                Color(0xFF0091FF)
                            } else {
                                Color.White.copy(alpha = if (tabSelected) 1f else 0.5f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        val textAlpha = if (!isBackdrop && tabSelected) 0f else 1f
                        Text(
                            text = "Settings",
                            color = if (isBackdrop && tabSelected) Color(0xFF0091FF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (tabSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.alpha(textAlpha)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryGridContent(
    viewModel: BookViewModel,
    backdrop: Backdrop,
    onReadBook: (String) -> Unit
) {
    val books by viewModel.books.collectAsState()
    var renamingBook by remember { mutableStateOf<Book?>(null) }
    var renameTitle by remember { mutableStateOf("") }

    var optionMenuBook by remember { mutableStateOf<Book?>(null) }
    var optionMenuOffset by remember { mutableStateOf(Offset.Zero) }

    var showClassicMenu by remember { mutableStateOf(false) }
    var classicMenuOffset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "vbook bookshelf",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                )

                if (books.isNotEmpty()) {
                    var barBtnOffset by remember { mutableStateOf(Offset.Zero) }
                    LiquidButton(
                        onClick = {
                            classicMenuOffset = barBtnOffset
                            showClassicMenu = true
                        },
                        backdrop = backdrop,
                        modifier = Modifier
                            .height(34.dp)
                            .width(110.dp)
                            .onGloballyPositioned { coords ->
                                barBtnOffset = coords.positionInRoot()
                            }
                            .clip(RoundedCornerShape(10.dp)),
                        isInteractive = true,
                        surfaceColor = Color.White.copy(0.12f)
                    ) {
                        Text(
                            "Classici ▾",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = (-0.1).sp
                        )
                    }
                }
            }

            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "Empty Bookshelf",
                            tint = Color.White.copy(0.12f),
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No books uploaded",
                            color = Color.White.copy(0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Upload a .epub book file from the Upload tab or import our built-in premium novel below!",
                            color = Color.White.copy(0.45f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))

                        var emptyBtnOffset by remember { mutableStateOf(Offset.Zero) }
                        LiquidButton(
                            onClick = {
                                classicMenuOffset = emptyBtnOffset
                                showClassicMenu = true
                            },
                            backdrop = backdrop,
                            modifier = Modifier
                                .height(44.dp)
                                .width(220.dp)
                                .onGloballyPositioned { coords ->
                                    emptyBtnOffset = coords.positionInRoot()
                                }
                                .border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp)),
                            isInteractive = true,
                            surfaceColor = Color.White.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "Carica Classico ▾",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = (-0.1).sp
                            )
                        }
                    }
                }
            } else {
                // Ensure bottom padding is exactly 120.dp so books rotating/scrolling are never hidden or obstructed
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 132.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { onReadBook(book.id) },
                            onShowOptions = { offset ->
                                optionMenuBook = book
                                optionMenuOffset = offset
                            }
                        )
                    }
                }
            }
        }

        // Rename Book input Dialog with beautiful Liquid glassmorphism
        renamingBook?.let { book ->
            Dialog(onDismissRequest = { renamingBook = null }) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(24.dp) },
                            effects = {
                                vibrancy()
                                blur(18f.dp.toPx())
                                lens(16f.dp.toPx(), 16f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color(0xFF0F1218).copy(alpha = 0.65f))
                            }
                        )
                        .border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(24.dp))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Rename Book",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = renameTitle,
                            onValueChange = { renameTitle = it },
                            placeholder = { Text("Book Title", color = Color.White.copy(0.4f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF0088FF),
                                unfocusedBorderColor = Color.White.copy(0.2f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { renamingBook = null }) {
                                Text("Cancel", color = Color.White.copy(0.6f))
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (renameTitle.trim().isNotEmpty()) {
                                        viewModel.renameBook(book.id, renameTitle.trim())
                                        renamingBook = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0088FF),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }

        LiquidGlassMenu(
            expanded = optionMenuBook != null,
            onDismissRequest = { optionMenuBook = null },
            anchorOffset = optionMenuOffset,
            backdrop = backdrop,
            book = optionMenuBook,
            onRename = { b ->
                optionMenuBook = null
                renamingBook = b
                renameTitle = b.title
            },
            onDelete = { b ->
                optionMenuBook = null
                viewModel.deleteBook(b.id)
            }
        )

        LiquidBookSelectMenu(
            expanded = showClassicMenu,
            onDismissRequest = { showClassicMenu = false },
            anchorOffset = classicMenuOffset,
            backdrop = backdrop,
            onSelectBook = { type ->
                loadClassicBook(viewModel, type)
                Toast.makeText(context, "Cargamento romanzo completato!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onShowOptions: (Offset) -> Unit
) {
    var optionButtonOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(Color(0xFF1A1F26))
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = File(book.coverPath),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FilePresent,
                        contentDescription = "EPUB No Cover",
                        tint = Color.White.copy(0.2f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            // Options menu button "•••"
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = { onShowOptions(optionButtonOffset) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(0.5f)
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .onGloballyPositioned { coordinates ->
                            optionButtonOffset = coordinates.positionInRoot()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        
        Text(
            text = book.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = book.author,
            color = Color.White.copy(0.5f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun UploadContent(viewModel: BookViewModel, backdrop: Backdrop) {
    val context = LocalContext.current
    val importing by viewModel.importing.collectAsState()
    val scope = rememberCoroutineScope()

    var showClassicMenu by remember { mutableStateOf(false) }
    var classicMenuOffset by remember { mutableStateOf(Offset.Zero) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val tempFile = File(context.cacheDir, "temp_import.epub")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    viewModel.importEpub(tempFile) {
                        Toast.makeText(context, "Book imported successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Failed to import EPUB: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Text(
                text = "EPUB Import Engine",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add digital EPUB publications to your local catalog",
                color = Color.White.copy(0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))

            // Premium Upload Interactive Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(24.dp) },
                        effects = {
                            vibrancy()
                            blur(20f.dp.toPx())
                            lens(16f.dp.toPx(), 16f.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color(0xFF0F1218).copy(alpha = 0.65f))
                        }
                    )
                    .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = !importing) {
                        filePicker.launch("application/epub+zip")
                    },
                contentAlignment = Alignment.Center
            ) {
                if (importing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF0091FF))
                        Spacer(Modifier.height(16.dp))
                        Text("Parsing & optimizing chapters...", color = Color.White.copy(0.7f), fontSize = 14.sp)
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Pick Folder",
                            tint = Color(0xFF0091FF),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Select .epub file",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Locally processes images, spines, and styles offline",
                            color = Color.White.copy(0.4f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Built-in Classic alternative:",
                    color = Color.White.copy(0.5f),
                    fontSize = 13.sp
                )
                var classicBtnOffset by remember { mutableStateOf(Offset.Zero) }
                LiquidButton(
                    onClick = {
                        if (!importing) {
                            classicMenuOffset = classicBtnOffset
                            showClassicMenu = true
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier
                        .height(38.dp)
                        .width(135.dp)
                        .onGloballyPositioned { coords ->
                            classicBtnOffset = coords.positionInRoot()
                        }
                        .border(1.dp, Color(0xFF0091FF).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    isInteractive = !importing,
                    surfaceColor = Color(0xFF0091FF).copy(alpha = 0.08f)
                ) {
                    Text(
                        "Classici ▾",
                        color = Color(0xFF0091FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        LiquidBookSelectMenu(
            expanded = showClassicMenu,
            onDismissRequest = { showClassicMenu = false },
            anchorOffset = classicMenuOffset,
            backdrop = backdrop,
            onSelectBook = { type ->
                loadClassicBook(viewModel, type)
                Toast.makeText(context, "Cargamento romanzo completato!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun loadDemoBook(viewModel: BookViewModel) {
    loadClassicBook(viewModel, "JekyllHyde")
}

private fun loadClassicBook(viewModel: BookViewModel, bookType: String) {
    viewModel.viewModelScope.launch {
        val id: String
        val title: String
        val author: String
        val pages: List<BookPage>

        when (bookType) {
            "TimeMachine" -> {
                id = "demo_time_machine"
                title = "The Time Machine"
                author = "H. G. Wells"
                pages = listOf(
                    BookPage(bookId = id, pageIndex = 0, chapterTitle = "Chapter 1: The Inventor", content = "The Time Traveller (for so it will be convenient to speak of him) was expounding a recondite matter to us. His grey eyes shone and twinkled, and his usually pale face was flushed and animated. The fire burned brightly, and the soft radiance of the incandescent lights in the lilies of silver caught the bubbles that flashed and passed in our glasses. Our chairs, being his patents, embraced and caressed us rather than submitted to be sat upon, and there was that luxurious after-dinner atmosphere when thought runs gracefully free of the trammels of precision."),
                    BookPage(bookId = id, pageIndex = 1, chapterTitle = "Chapter 2: The Fourth Dimension", content = "‘You must follow me carefully.’ said the Time Traveller. ‘I shall have to controvert one or two ideas that are almost universally accepted. The geometry, for instance, they taught you at school is founded on a misconception. Is it not clear that Space, as our mathematicians have it, is spoken of as having three dimensions, which one may call Length, Breadth, and Thickness? But there is also a fourth: of Time.’"),
                    BookPage(bookId = id, pageIndex = 2, chapterTitle = "Chapter 3: The Machine", content = "‘But the difficulty is this,’ said the Psychologist. ‘You cannot move about in Time.’ ‘No,’ said the Time Traveller, ‘but that is simply because our mental limitations prevent it of doing so. It is simply a matter of technical leverage. And I have designed the machine to do precisely that extreme physical transportation!’")
                )
            }
            "Alice" -> {
                id = "demo_alice"
                title = "Alice in Wonderland"
                author = "Lewis Carroll"
                pages = listOf(
                    BookPage(bookId = id, pageIndex = 0, chapterTitle = "Chapter 1: Down the Rabbit-Hole", content = "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, ‘and what is the use of a book,’ thought Alice ‘without pictures or conversations?’"),
                    BookPage(bookId = id, pageIndex = 1, chapterTitle = "Chapter 2: The White Rabbit", content = "Suddenly, a White Rabbit with pink eyes ran close by her. There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say to itself, ‘Oh dear! Oh dear! I shall be late!’ But when the Rabbit actually took a watch out of its waistcoat-pocket, Alice started to her feet!"),
                    BookPage(bookId = id, pageIndex = 2, chapterTitle = "Chapter 3: The Caucus-Race", content = "They were indeed a queer-looking party that assembled on the bank—the birds with draggled feathers, the animals with their fur clinging close to them, and all dripping wet, cross, and uncomfortable. The first question of course was, how to get dry again: they had a consultation about it.")
                )
            }
            else -> {
                id = "demo_jekyll_hyde"
                title = "Strange Case of Dr Jekyll & Mr Hyde"
                author = "Robert Louis Stevenson"
                pages = listOf(
                    BookPage(bookId = id, pageIndex = 0, chapterTitle = "Chapter 1: Story of the Door", content = "Mr. Utterson the lawyer was a man of a rugged countenance that was never lighted by a smile; cold, scanty and embarrassed in discourse; backward in sentiment; lean, long, dusty, dreary and yet somehow lovable. At friendly meetings, and when the wine was to his taste, something eminently human beaconed from his eye; something indeed which never found its way into his talk, but which spoke not only in these silent symbols of the after-dinner face, but more often and loudly in the acts of his life."),
                    BookPage(bookId = id, pageIndex = 1, chapterTitle = "Chapter 1: Story of the Door", content = "He was austere with himself; drank gin when he was alone, to mortify a taste for vintages; and though he enjoyed the theater, had not crossed the doors of one for twenty years. But he had an approved tolerance for others; sometimes wondering, almost with envy, at the high pressure of spirits involved in their misdeeds; and in any extremity inclined to help rather than to reprove. \"I incline to Cain's heresy,\" he used to say quaintly: \"I let my brother go to the devil in his own way.\""),
                    BookPage(bookId = id, pageIndex = 2, chapterTitle = "Chapter 1: Story of the Door", content = "In this character, it was frequently his fortune to be the last reputable acquaintance and the last good influence in the lives of downgoing men. And to such as these, so long as they came about his chambers, he never marked a shade of change in his demeanour."),
                    BookPage(bookId = id, pageIndex = 3, chapterTitle = "Chapter 2: Search for Mr. Hyde", content = "That evening Mr. Utterson came home to his bachelor chamber in sombreness of spirit and sat down to dinner without relish. It was his custom of a Sunday, when this meal was over, to sit close by the fire, a volume of some dry divinity on his reading desk, until the clock of the neighbouring church rang out the hour of twelve, when he would go soberly and gratefully to bed."),
                    BookPage(bookId = id, pageIndex = 4, chapterTitle = "Chapter 2: Search for Mr. Hyde", content = "On this night however, as soon as the cloth was taken away, he took up a candle and went into his business room. There he opened his safe, took from the private part of it a document containing Dr. Jekyll's Will, and sat down with a clouded brow to study its contents. The will was holograph, providing that, in case of the decease of Henry Jekyll, M.D., D.C.L., LL.D., F.R.S., etc., all his possessions were to pass into the hands of his \"friend and benefactor Edward Hyde.\""),
                    BookPage(bookId = id, pageIndex = 5, chapterTitle = "Chapter 3: Dr. Jekyll was Quiet at Ease", content = "A fortnight later, by excellent good fortune, the doctor gave one of his pleasant dinners to some five or six old cronies, all intelligent, reputable men and all judges of good wine; and Mr. Utterson so contrived that he remained behind after the others had departed. This was no new thing, but a thing that had befallen many scores of times. Where Utterson was liked, he was liked well. Hosts loved to detain the dry lawyer, when the light-hearted and loose-tongued had already their foot on the threshold; they liked to sit a while in his unobtrusive company, practising for solitude."),
                    BookPage(bookId = id, pageIndex = 6, chapterTitle = "Chapter 3: Dr. Jekyll was Quiet at Ease", content = "\"I have been wanting to speak to you, Jekyll,\" began Utterson. \"You know that will of yours?\" A close observer might have gathered that the topic was distasteful; but the doctor quite ignored it. \"My poor Utterson,\" said he, \"you are unfortunate in such a client. I never saw a man so distressed as you were by my will; unless it were Lanyon, at my scientific heresies. Oh, I always mean to see more of him; but he is a hidebound pedant for all that; an ignorant, blatant pedant. I was never more disappointed.\"")
                )
            }
        }

        val db = AppDatabase.getDatabase(viewModel.getApplication())
        db.bookDao().insertBook(
            Book(
                id = id,
                title = title,
                author = author,
                coverPath = null
            )
        )
        db.bookDao().deletePagesForBook(id)
        db.bookDao().insertPages(pages)
    }
}

@Composable
fun SettingsContent(viewModel: BookViewModel, backdrop: Backdrop) {
    val fontSize by viewModel.fontSize.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val fontFamily by viewModel.fontFamily.collectAsState()
    val lineSpacing by viewModel.lineSpacing.collectAsState()
    val textAlign by viewModel.textAlign.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Preferences",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    // Theme Card
                    SettingsCard(title = "Reader Themes") {
                        val themesList = listOf("Paper", "Sepia", "Slate", "Cosmic")
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themesList.forEach { thName ->
                                val selected = theme == thName
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when (thName) {
                                                "Paper" -> Color.White
                                                "Sepia" -> Color(0xFFF4ECD8)
                                                "Slate" -> Color(0xFF2E333F)
                                                "Cosmic" -> Color(0xFF0F1116)
                                                else -> Color.Gray
                                            }
                                        )
                                        .border(
                                            2.dp,
                                            if (selected) Color(0xFF0091FF) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setTheme(thName) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = thName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (thName == "Paper" || thName == "Sepia") Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    // Font size slider card
                    SettingsCard(title = "Font Size Modifier") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("A", color = Color.White.copy(0.4f), fontSize = 12.sp)
                            Spacer(Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                LiquidSlider(
                                    value = { fontSize },
                                    onValueChange = { viewModel.setFontSize(it) },
                                    valueRange = 12f..32f,
                                    visibilityThreshold = 0.01f,
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("A", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Current Scale: ${fontSize.toInt()} sp",
                            color = Color.White.copy(0.5f),
                            fontSize = 12.sp
                        )
                    }
                }

                item {
                    SettingsCard(title = "Typography Pairing") {
                        val fonts = listOf("Sans", "Serif", "Monospace", "Georgia")
                        val selectedIdx = when (fontFamily) {
                            "Sans", "SansSerif" -> 0
                            "Serif" -> 1
                            "Monospace" -> 2
                            "Georgia" -> 3
                            else -> 0
                        }
                        LiquidSegmentedControl(
                            selectedOptionIndex = { selectedIdx },
                            onOptionSelected = { index ->
                                val selected = when (index) {
                                    0 -> "Sans"
                                    1 -> "Serif"
                                    2 -> "Monospace"
                                    3 -> "Georgia"
                                    else -> "Sans"
                                }
                                viewModel.setFontFamily(selected)
                            },
                            options = fonts,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )
                    }
                }

                item {
                    SettingsCard(title = "Line Spacing Configuration") {
                        val spacingValNames = listOf("Compact", "Normal", "Wide")
                        val selectedIdx = when {
                            lineSpacing <= 1.3f -> 0
                            lineSpacing <= 1.6f -> 1
                            else -> 2
                        }
                        LiquidSegmentedControl(
                            selectedOptionIndex = { selectedIdx },
                            onOptionSelected = { index ->
                                val selected = when (index) {
                                    0 -> 1.2f
                                    1 -> 1.5f
                                    2 -> 1.8f
                                    else -> 1.5f
                                }
                                viewModel.setLineSpacing(selected)
                            },
                            options = spacingValNames,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )
                    }
                }

                item {
                    SettingsCard(title = "Text Alignment Formatting") {
                        val alignmentNames = listOf("Left", "Center", "Justified")
                        val selectedIdx = when (textAlign) {
                            "Left" -> 0
                            "Center" -> 1
                            "Justified" -> 2
                            else -> 2
                        }
                        LiquidSegmentedControl(
                            selectedOptionIndex = { selectedIdx },
                            onOptionSelected = { index ->
                                val selected = when (index) {
                                    0 -> "Left"
                                    1 -> "Center"
                                    2 -> "Justified"
                                    else -> "Justified"
                                }
                                viewModel.setTextAlign(selected)
                            },
                            options = alignmentNames,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1F2531).copy(0.35f),
        border = BorderStroke(1.dp, Color.White.copy(0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = Color.White.copy(0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ReaderContent(
    bookId: String,
    viewModel: BookViewModel,
    backdrop: Backdrop,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Loaded books and pages
    val pages by viewModel.getPagesForBook(bookId).collectAsState(initial = emptyList())
    val savedPoints by viewModel.getSavedPointsForBook(bookId).collectAsState(initial = emptyList())

    // SharedPreferences reader settings
    val fontSizeScale by viewModel.fontSize.collectAsState()
    val activeTheme by viewModel.theme.collectAsState()
    val fontFamilySelection by viewModel.fontFamily.collectAsState()
    val lineSpacingScale by viewModel.lineSpacing.collectAsState()
    val textAlignSelection by viewModel.textAlign.collectAsState()

    // Book Detail info
    var currentBookTitle by remember { mutableStateOf("Reader View") }
    LaunchedEffect(bookId) {
        withContext(Dispatchers.IO) {
            val b = AppDatabase.getDatabase(context).bookDao().getBookById(bookId)
            b?.let {
                currentBookTitle = it.title
            }
        }
    }

    // Book Theme mappings
    val paperColor = when (activeTheme) {
        "Paper" -> Color(0xFFFAFAFA)
        "Sepia" -> Color(0xFFF6EEDC)
        "Slate" -> Color(0xFF1F242F)
        "Cosmic" -> Color(0xFF0F1218)
        else -> Color(0xFF0F1218)
    }

    val inkColor = when (activeTheme) {
        "Paper" -> Color(0xFF161B25)
        "Sepia" -> Color(0xFF3C2C1D)
        "Slate" -> Color(0xFFECEFF4)
        "Cosmic" -> Color(0xFF90A4AE).copy(0.85f)
        else -> Color(0xFFECEFF4)
    }

    // Drawer state ("Punti Salvati" landmarks drawer)
    var showBookmarksDrawer by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // Track pending character teleport coordinates
    var pendingScrollToChar by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(paperColor)
            .statusBarsPadding()
    ) {
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = inkColor)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidButton(
                        onClick = onClose,
                        backdrop = backdrop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape),
                        isInteractive = true,
                        surfaceColor = Color.White.copy(0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Chiudi",
                            tint = inkColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = currentBookTitle,
                        color = inkColor.copy(0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                }

                // Horizontal swiper horizontal-pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { pageIdx ->
                    val page = pages.getOrNull(pageIdx)
                    page?.let { p ->
                        val scrollState = rememberScrollState()
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        var pressOffset by remember { mutableStateOf<Offset?>(null) }
                        var highlightedWordRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                        var highlightRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

                        val pointsOnThisPage = remember(savedPoints, pageIdx) {
                            savedPoints.filter { it.pageIndex == pageIdx }
                        }

                        // Seamless cross-page scroll calculation & centering teleportation logic
                        LaunchedEffect(pendingScrollToChar, textLayoutResult) {
                            val charIdx = pendingScrollToChar
                            val tlr = textLayoutResult
                            if (charIdx != null && tlr != null && pageIdx == pagerState.currentPage) {
                                val lineIndex = tlr.getLineForOffset(charIdx.coerceIn(0, tlr.layoutInput.text.length - 1))
                                val lineTopPx = tlr.getLineTop(lineIndex)
                                val targetScrollPx = (lineTopPx - 200f).coerceAtLeast(0f)
                                scrollState.animateScrollTo(targetScrollPx.roundToInt())

                                // Visually flash high contrast glass indicator on that specific word
                                val wordRange = getWordRangeAtOffset(tlr.layoutInput.text.toString(), charIdx)
                                if (wordRange.first in 0..wordRange.second && wordRange.second < tlr.layoutInput.text.length) {
                                    val firstCharRect = tlr.getBoundingBox(wordRange.first)
                                    val lastCharRect = tlr.getBoundingBox(wordRange.second)
                                    highlightedWordRange = wordRange
                                    highlightRect = if (firstCharRect != null && lastCharRect != null) {
                                        androidx.compose.ui.geometry.Rect(
                                            left = firstCharRect.left,
                                            top = firstCharRect.top,
                                            right = lastCharRect.right,
                                            bottom = lastCharRect.bottom
                                        )
                                    } else null
                                }
                                pendingScrollToChar = null
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 24.dp)
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = p.chapterTitle,
                                    fontSize = (fontSizeScale * 1.35f).sp,
                                    color = inkColor,
                                    fontWeight = FontWeight.Black,
                                    style = TextStyle(
                                        fontFamily = when (fontFamilySelection) {
                                            "Sans", "SansSerif" -> FontFamily.SansSerif
                                            "Serif" -> FontFamily.Serif
                                            "Monospace" -> FontFamily.Monospace
                                            "Georgia" -> FontFamily.Serif
                                            else -> FontFamily.SansSerif
                                        }
                                    )
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val annotatedContent = remember(p.content, highlightedWordRange, pointsOnThisPage) {
                                        buildAnnotatedString {
                                            append(p.content)
                                            // 1. Highlight all saved bookmarked points on this page
                                            for (sp in pointsOnThisPage) {
                                                val range = getWordRangeAtOffset(p.content, sp.charIndex)
                                                if (range.first in 0..range.second && range.second < p.content.length) {
                                                    addStyle(
                                                        style = SpanStyle(
                                                            background = Color(0xFFFDD835).copy(alpha = 0.35f), // Yellow marker highlighted
                                                            textDecoration = TextDecoration.Underline, // Underlined
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        start = range.first,
                                                        end = range.second + 1
                                                    )
                                                }
                                            }
                                            // 2. Highlight currently active pulsed word
                                            highlightedWordRange?.let { range ->
                                                if (range.first in 0..range.second && range.second < p.content.length) {
                                                    addStyle(
                                                        style = SpanStyle(
                                                            background = Color(0xFFFDD835).copy(alpha = 0.45f), // Yellow marker highlighted
                                                            textDecoration = TextDecoration.Underline, // Underlined
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        start = range.first,
                                                        end = range.second + 1
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Display premium Liquid backdrop highlight overlay over coordinates (drawn first - in background)
                                    highlightRect?.let { hr ->
                                        Box(
                                            modifier = Modifier
                                                .offset {
                                                    IntOffset(
                                                        hr.left.roundToInt(),
                                                        hr.top.roundToInt()
                                                    )
                                                }
                                                .size(
                                                    width = with(LocalDensity.current) { hr.width.toDp() },
                                                    height = with(LocalDensity.current) { hr.height.toDp() }
                                                )
                                                .drawBackdrop(
                                                    backdrop = backdrop,
                                                    shape = { RoundedCornerShape(6.dp) },
                                                    effects = {
                                                        vibrancy()
                                                        blur(6f.dp.toPx())
                                                        lens(6f.dp.toPx(), 6f.dp.toPx())
                                                    },
                                                    onDrawSurface = {
                                                        drawRect(Color(0xFFFDD835).copy(alpha = 0.35f))
                                                     }
                                                 )
                                                 .border(1.dp, Color(0xFFFBC02D).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                         )
                                     }

                                    Text(
                                        text = annotatedContent,
                                        fontSize = fontSizeScale.sp,
                                        color = inkColor.copy(0.9f),
                                        lineHeight = (fontSizeScale * lineSpacingScale).sp,
                                        textAlign = when (textAlignSelection) {
                                            "Left" -> TextAlign.Left
                                            "Center" -> TextAlign.Center
                                            "Justified" -> TextAlign.Justify
                                            else -> TextAlign.Justify
                                        },
                                        style = TextStyle(
                                            fontFamily = when (fontFamilySelection) {
                                                "Sans", "SansSerif" -> FontFamily.SansSerif
                                                "Serif" -> FontFamily.Serif
                                                "Monospace" -> FontFamily.Monospace
                                                "Georgia" -> FontFamily.Serif
                                                else -> FontFamily.SansSerif
                                            }
                                        ),
                                        onTextLayout = { textLayoutResult = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(p.id) {
                                                detectTapGestures(
                                                    onTap = {
                                                        highlightedWordRange = null
                                                        highlightRect = null
                                                    },
                                                    onLongPress = { offset ->
                                                        val tlr = textLayoutResult
                                                        if (tlr != null) {
                                                            val charIdx = tlr.getOffsetForPosition(offset)
                                                            if (charIdx in 0 until p.content.length) {
                                                                val word = getWordAtOffset(p.content, charIdx)
                                                                
                                                                // Trigger tactile haptic feedback vibration safely in a try-catch
                                                                try {
                                                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                                                    } else {
                                                                        @Suppress("DEPRECATION")
                                                                        vibrator?.vibrate(100)
                                                                    }
                                                                } catch (e: Exception) {
                                                                    // Silent fallback
                                                                }

                                                                // Commit bookmark word points to local DB
                                                                viewModel.savePoint(bookId, pageIdx, p.chapterTitle, word, charIdx)

                                                                // Highlight visually with yellow underlined state range representation
                                                                val wordRange = getWordRangeAtOffset(p.content, charIdx)
                                                                if (wordRange.first in 0..wordRange.second && wordRange.second < p.content.length) {
                                                                    val firstCharRect = tlr.getBoundingBox(wordRange.first)
                                                                    val lastCharRect = tlr.getBoundingBox(wordRange.second)
                                                                    highlightedWordRange = wordRange
                                                                    highlightRect = if (firstCharRect != null && lastCharRect != null) {
                                                                        androidx.compose.ui.geometry.Rect(
                                                                            left = firstCharRect.left,
                                                                            top = firstCharRect.top,
                                                                            right = lastCharRect.right,
                                                                            bottom = lastCharRect.bottom
                                                                        )
                                                                     } else null
                                                                  }
                                                              }
                                                          }
                                                      }
                                                  )
                                              }
                                            .pointerInput(p.id) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown()
                                                    pressOffset = down.position
                                                    val job = scope.launch {
                                                        delay(3000) // Precise 3 seconds long-press threshold
                                                        val tapOffset = pressOffset
                                                        val tlr = textLayoutResult
                                                        if (tapOffset != null && tlr != null) {
                                                            val charIdx = tlr.getOffsetForPosition(tapOffset)
                                                            if (charIdx in 0 until p.content.length) {
                                                                val word = getWordAtOffset(p.content, charIdx)
                                                                
                                                                // Trigger tactile haptic feedback vibration
                                                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                                                } else {
                                                                    @Suppress("DEPRECATION")
                                                                    vibrator?.vibrate(100)
                                                                }

                                                                // Commit bookmark word points to local DB
                                                                viewModel.savePoint(bookId, pageIdx, p.chapterTitle, word, charIdx)

                                                                // Highlight visually with Liquid frame
                                                                val wordRange = getWordRangeAtOffset(p.content, charIdx)
                                                                if (wordRange.first in 0..wordRange.second && wordRange.second < p.content.length) {
                                                                    val firstCharRect = tlr.getBoundingBox(wordRange.first)
                                                                    val lastCharRect = tlr.getBoundingBox(wordRange.second)
                                                                    highlightedWordRange = wordRange
                                                                    highlightRect = if (firstCharRect != null && lastCharRect != null) {
                                                                        androidx.compose.ui.geometry.Rect(
                                                                            left = firstCharRect.left,
                                                                            top = firstCharRect.top,
                                                                            right = lastCharRect.right,
                                                                            bottom = lastCharRect.bottom
                                                                        )
                                                                    } else null
                                                                }
                                                            }
                                                        }
                                                    }
                                                    waitForUpOrCancellation()
                                                    job.cancel()
                                                }
                                            }
                                    )
                                }
                                Spacer(modifier = Modifier.height(100.dp))
                            }
                        }
                    }
                }

                // Control hub with liquid-glass aesthetic buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Page indicator as a beautiful small glass pill
                    Box(
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(12.dp) },
                                effects = {
                                    vibrancy()
                                    blur(6f.dp.toPx())
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = 0.08f))
                                }
                            )
                            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${pages.size}",
                            color = inkColor.copy(0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Bottom Navigation actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReaderLiquidGlassButton(
                            text = "Torna",
                            onClick = onClose,
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f)
                        )
                        ReaderLiquidGlassButton(
                            text = "Punti",
                            onClick = { showBookmarksDrawer = true },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Float sheet landmarks list drawer ("Punti Salvati" overlays)
        AnimatedVisibility(
            visible = showBookmarksDrawer,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }, animationSpec = spring(1.1f, 250f)),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }, animationSpec = spring(1.1f, 250f)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showBookmarksDrawer = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {}
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) },
                            effects = {
                                vibrancy()
                                blur(20f.dp.toPx())
                                lens(16f.dp.toPx(), 16f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color(0xFF0F1218).copy(alpha = 0.65f))
                            }
                        )
                        .border(
                            BorderStroke(1.dp, Color.White.copy(0.12f)),
                            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Punti Salvati",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            IconButton(onClick = { showBookmarksDrawer = false }) {
                                Icon(Icons.Default.Close, "Chiudi", tint = Color.White)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (savedPoints.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Nessun punto salvato. Tiene premuto per 3 secondi su una parola del testo per salvarla!",
                                    color = Color.White.copy(0.4f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(savedPoints, key = { it.id }) { pt ->
                                    // LiquidButton provides high-fidelity fluid distortion ripple feedback on click
                                    LiquidButton(
                                        onClick = {
                                            scope.launch {
                                                pagerState.scrollToPage(pt.pageIndex)
                                                pendingScrollToChar = pt.charIndex
                                            }
                                            showBookmarksDrawer = false
                                        },
                                        backdrop = backdrop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(84.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        isInteractive = true,
                                        surfaceColor = Color.White.copy(0.04f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                                Text(
                                                    pt.chapterTitle,
                                                    color = Color(0xFF0091FF),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    pt.shortSnippet,
                                                    color = Color.White.copy(0.85f),
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    "Pagina ${pt.pageIndex + 1} • Carattere ${pt.charIndex}",
                                                    color = Color.White.copy(0.45f),
                                                    fontSize = 11.sp
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.deletePoint(pt.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Landmark",
                                                    tint = Color(0xFFFF4D4D),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helpers for word selection
fun getWordAtOffset(text: String, characterIndex: Int): String {
    if (characterIndex < 0 || characterIndex >= text.length) return "Saved Quote"
    var start = characterIndex
    while (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] != '.' && text[start - 1] != ',' && text[start - 1] != ';' && text[start - 1] != '"' && text[start - 1] != '\'' && text[start - 1] != '?' && text[start - 1] != '!') {
        start--
    }
    var end = characterIndex
    while (end < text.length - 1 && !text[end + 1].isWhitespace() && text[end + 1] != '.' && text[end + 1] != ',' && text[end + 1] != ';' && text[end + 1] != '"' && text[end + 1] != '\'' && text[end + 1] != '?' && text[end + 1] != '!') {
        end++
    }
    return text.substring(start, end + 1).trim()
}

fun getWordRangeAtOffset(text: String, characterIndex: Int): Pair<Int, Int> {
    if (characterIndex < 0 || characterIndex >= text.length) return Pair(0, 0)
    var start = characterIndex
    while (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] != '.' && text[start - 1] != ',' && text[start - 1] != ';' && text[start - 1] != '"' && text[start - 1] != '\'' && text[start - 1] != '?' && text[start - 1] != '!') {
        start--
    }
    var end = characterIndex
    while (end < text.length - 1 && !text[end + 1].isWhitespace() && text[end + 1] != '.' && text[end + 1] != ',' && text[end + 1] != ';' && text[end + 1] != '"' && text[end + 1] != '\'' && text[end + 1] != '?' && text[end + 1] != '!') {
        end++
    }
    return Pair(start, end)
}

@Composable
fun ReaderLiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier
            .height(38.dp),
        isInteractive = true,
        surfaceColor = Color.White
    ) {
        Text(
            text = text,
            color = Color.Black, // Strictly Color.Black for maximum contrast/legibility
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = (-0.1).sp
        )
    }
}
