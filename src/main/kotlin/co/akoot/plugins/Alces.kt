package co.akoot.plugins

import com.destroystokyo.paper.event.block.BlockDestroyEvent
import io.papermc.paper.event.block.BlockBreakBlockEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class Alces : JavaPlugin(), Listener {

    private lateinit var serializer: GsonComponentSerializer

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        serializer = GsonComponentSerializer.gson()
    }

    @EventHandler
    fun BlockPlaceEvent.onBlockPlace() {
        if (isCancelled) return

        val item = itemInHand
        if (!item.type.isBlock) return

        val meta = item.itemMeta ?: return
        val displayName = meta.displayName()?.let { serializer.serialize(it) }
        val lore = meta.lore()?.mapNotNull { serializer.serialize(it) }?.joinToString("\n")

        if (displayName == null && lore == null) return

        val data = "${displayName ?: "-"}\n${lore ?: "-"}"
        getPDC(block).set(getKey(block.location), PersistentDataType.STRING, data)
    }

    @EventHandler
    fun BlockBreakEvent.onBlockBreak() {
        if (isCancelled) return
        handleBlockEvent(block, null, player.gameMode == GameMode.SURVIVAL)
    }

    @EventHandler
    fun BlockBreakBlockEvent.onBlockBreakBlock() {
        handleBlockEvent(block)
        drops.clear()
    }

    @EventHandler
    fun BlockDestroyEvent.onBlockDestroy() {
        if (isCancelled) return
        handleBlockEvent(block)
        setWillDrop(false)
    }

    @EventHandler
    fun EntityExplodeEvent.onExplosion() {
        if (isCancelled) return
        if (entity.type.name != "WIND_CHARGE") {
            for (block in blockList()) {
                handleBlockEvent(block)
            }
        }
    }

    @EventHandler
    fun BlockPistonRetractEvent.pistonRetract() {
        if (isCancelled) return
        blocks.forEach { block ->
            handleBlockEvent(block, direction)
        }
    }

    @EventHandler
    fun BlockPistonExtendEvent.pistonExtend() {
        if (isCancelled) return
        blocks.forEach { block ->
            handleBlockEvent(block, direction)
        }
    }

    private fun handleBlockEvent(block: Block, direction: BlockFace? = null, shouldDrop: Boolean = true) {
        val pdc = getPDC(block)
        val key = getKey(block.location)

        val data = pdc.get(key, PersistentDataType.STRING) ?: return

        if (direction != null) {
            val newLocation =
                block.location.add(direction.modX.toDouble(), direction.modY.toDouble(), direction.modZ.toDouble())
            val newBlock = newLocation.block
            val newBlockPdc = getPDC(newBlock)

            pdc.remove(key)
            runNextTick { newBlockPdc.set(getKey(newBlock.location), PersistentDataType.STRING, data) }

        } else {
            if (shouldDrop) {
                val lines = data.split("\n")
                val displayName = lines[0]
                val lore = lines.drop(1)

                val drop = block.drops.first()
                val itemMeta = drop.itemMeta

                if (displayName != "-") itemMeta.displayName(serializer.deserialize(displayName))
                if (lore.isNotEmpty() && lore[0] != "-") itemMeta.lore(lore.map { serializer.deserialize(it) })

                drop.itemMeta = itemMeta
                block.location.world.dropItemNaturally(block.location, drop)
            }
            block.type = Material.AIR
            pdc.remove(key)
        }
    }

    private fun getPDC(block: Block): PersistentDataContainer {
        return block.chunk.persistentDataContainer
    }

    private fun getKey(location: Location): NamespacedKey {
        val key = "${location.world.name}.${location.blockX}.${location.blockY}.${location.blockZ}"
        return NamespacedKey("alces", key)
    }

    private fun runNextTick(task: Runnable) {
        server.scheduler.runTaskLater(this, task, 1)
    }
}