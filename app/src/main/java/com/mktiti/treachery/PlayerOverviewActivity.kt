package com.mktiti.treachery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.locks.ReentrantLock

class PlayerOverviewActivity : AppCompatActivity() {

    private companion object {
        const val START_HAND = 1
        const val DATA_KEY = "hands_data"
    }

    private val addLock = ReentrantLock()

    private lateinit var playerAdapter: PlayerAdapter
    private lateinit var playerList: RecyclerView

    private lateinit var playerAdd: FloatingActionButton
    // private lateinit var cardAdd: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_overview)
        setSupportActionBar(findViewById(R.id.toolbar))

        val state = (
                savedInstanceState?.getString(DATA_KEY) ?:
                intent.extras?.getString(DATA_KEY) ?:
                getPreferences(Context.MODE_PRIVATE)?.getString(DATA_KEY, null)
            )?.let { HandState.parse(it).hands }
                ?: Player.values().map { it.startHand() }


        playerAdapter = PlayerAdapter(ResourceLoader.getIconManager(this), state, this::playerClick, this::onPlayerDelete)
        playerList = findViewById<RecyclerView>(R.id.players_list).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = playerAdapter
        }
        val callback = SwipeDeleteCallback {
            playerAdapter -= it
        }
        ItemTouchHelper(callback).attachToRecyclerView(playerList)
/*
        cardAdd = findViewById<FloatingActionButton>(R.id.card_add).apply {
            setOnClickListener { view ->
                SelectUtil.promptCard(this@PlayerOverviewActivity, supportFragmentManager) {
                    Toast.makeText(this@PlayerOverviewActivity, it.niceName, Toast.LENGTH_LONG).show()
                }
            }
        }

 */

        playerAdd = findViewById<FloatingActionButton>(R.id.player_add).apply {
            setOnClickListener {
                val available: List<Player> = Player.values().toList() - playerAdapter.stored.map { it.player }
                SelectUtil.promptHouse(this@PlayerOverviewActivity, available) { player ->
                    addPlayer(player.startHand())
                }

                /*
                SelectUtil.promptSelect(this@PlayerOverviewActivity, resources.getString(R.string.add_player_title), available, Player::niceName) { added ->
                    addPlayer(added.startHand())
                }
                 */
            }
        }

        onPlayerUpdate()
    }

    private fun canAdd(player: Player?) =
        playerAdapter.stored.size < Player.values().size &&
        (player == null || playerAdapter.stored.find { it.player == player} == null)

    private fun guardedAddAction(player: Player, action: () -> Unit) {
        if (canAdd(player)) {
            synchronized(addLock) {
                if (canAdd(player)) {
                    action()
                    onPlayerUpdate()
                }
            }
        }
    }

    private fun addPlayer(hand: PlayerHand) {
        guardedAddAction(hand.player) {
            playerAdapter += hand
        }
    }

    private fun playerClick(player: PlayerHand) {
        val intent = Intent(this, PlayerHandActivity::class.java)
        intent.putExtra(PlayerHandActivity.HAND_DATA_KEY, player.json())
        startActivityForResult(intent, START_HAND)
    }

    private fun onPlayerUpdate() {
        if (canAdd(null)) {
            playerAdd.show()
        } else {
            playerAdd.hide()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == START_HAND) {
            if (resultCode == Activity.RESULT_OK) {
                val jsonData = data?.getStringExtra(PlayerHandActivity.HAND_DATA_KEY) ?: return
                val hand = PlayerHand.parse(jsonData)
                playerAdapter.update(hand)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(DATA_KEY, HandState(playerAdapter.stored).json())
    }

    override fun onStop() {
        getPreferences(Context.MODE_PRIVATE)?.edit()?.apply {
            putString(DATA_KEY, HandState(playerAdapter.stored).json())
            apply()
        }

        super.onStop()
    }

    private fun onPlayerDelete(player: Player, undo: () -> Unit) {
        onPlayerUpdate()

        Snackbar.make(findViewById(R.id.player_coord), R.string.player_removed, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.player_remove_undo) {
                guardedAddAction(player, undo)
            }
            show()
        }
    }

}