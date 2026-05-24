package com.kyant.backdrop.catalog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "book_pages")
data class BookPage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: String,
    val pageIndex: Int,
    val chapterTitle: String,
    val content: String
)

@Entity(tableName = "saved_points")
data class SavedPoint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: String,
    val pageIndex: Int,
    val chapterTitle: String,
    val shortSnippet: String,
    val charIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedTime DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    @Update
    suspend fun updateBook(book: Book)

    @Query("UPDATE books SET title = :newTitle WHERE id = :bookId")
    suspend fun renameBook(bookId: String, newTitle: String)

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): Book?

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    // Book Pages
    @Query("SELECT * FROM book_pages WHERE bookId = :bookId ORDER BY pageIndex ASC")
    fun getPagesForBook(bookId: String): Flow<List<BookPage>>

    @Query("SELECT * FROM book_pages WHERE bookId = :bookId ORDER BY pageIndex ASC")
    suspend fun getPagesForBookDirect(bookId: String): List<BookPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<BookPage>)

    @Query("DELETE FROM book_pages WHERE bookId = :bookId")
    suspend fun deletePagesForBook(bookId: String)

    // Saved Points
    @Query("SELECT * FROM saved_points WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getSavedPointsForBook(bookId: String): Flow<List<SavedPoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPoint(point: SavedPoint)

    @Query("DELETE FROM saved_points WHERE id = :id")
    suspend fun deleteSavedPoint(id: Int)
}

@Database(entities = [Book::class, BookPage::class, SavedPoint::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vbook_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
