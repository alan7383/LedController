package com.example.ledcontroller.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ledcontroller.R
import java.util.UUID

data class LightPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hue: Float,
    val sat: Float,
    val bri: Float,
    val iconName: String
)

enum class PresetIcon(val icon: ImageVector, val labelRes: Int) {
    SUNNY(Icons.Rounded.WbSunny, R.string.icon_sunny),
    FIRE(Icons.Rounded.LocalFireDepartment, R.string.icon_fire),
    WATER(Icons.Rounded.WaterDrop, R.string.icon_water),
    FOREST(Icons.Rounded.Forest, R.string.icon_forest),
    NIGHT(Icons.Rounded.Bedtime, R.string.icon_night),
    STAR(Icons.Rounded.Star, R.string.icon_star),
    HEART(Icons.Rounded.Favorite, R.string.icon_love),
    BOLT(Icons.Rounded.Bolt, R.string.icon_energy),
    HOME(Icons.Rounded.Home, R.string.icon_home),
    LIVING(Icons.Rounded.Weekend, R.string.icon_living),
    KITCHEN(Icons.Rounded.Kitchen, R.string.icon_kitchen),
    BATH(Icons.Rounded.Bathtub, R.string.icon_bath),
    BEDROOM(Icons.Rounded.SingleBed, R.string.icon_bedroom),
    DESK(Icons.Rounded.Desk, R.string.icon_desk),
    GAME(Icons.Rounded.Gamepad, R.string.icon_game),
    MOVIE(Icons.Rounded.Movie, R.string.icon_movie),
    BOOK(Icons.Rounded.MenuBook, R.string.icon_book),
    MUSIC(Icons.Rounded.MusicNote, R.string.icon_music),
    WORK(Icons.Rounded.Work, R.string.icon_work),
    COMPUTER(Icons.Rounded.Computer, R.string.icon_computer),
    PARTY(Icons.Rounded.Celebration, R.string.icon_party),
    COFFEE(Icons.Rounded.Coffee, R.string.icon_coffee)
}