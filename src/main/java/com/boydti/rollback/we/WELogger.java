package com.boydti.rollback.we;

import com.boydti.fawe.object.FaweChunk;
import com.boydti.fawe.object.RunnableVal2;
import com.boydti.fawe.object.changeset.FaweChangeSet;
import com.boydti.rollback.config.Loggers;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EditSession.Stage;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.util.eventbus.Subscribe;

public class WELogger {
    
    public WELogger() {
        if (Loggers.WORLDEDIT.use()) {
            WorldEdit.getInstance().getEventBus().register(this);
        }
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != Stage.BEFORE_HISTORY) {
            return;
        }
        Actor actor = event.getActor();
        EditSession session = event.getEditSession();
        if (session == null) {
            return;
        }
        ChangeSet fcs = session.getChangeSet();
        if (fcs != null && actor != null && actor.isPlayer()) {
            final RunnableVal2<FaweChunk, FaweChunk> existing = session.getQueue().getChangeTask();
            RollbackChangeSet logger = new RollbackChangeSet(actor.getName(), (FaweChangeSet) fcs);
            logger.addChangeTask(session.getQueue());
            final RunnableVal2<FaweChunk, FaweChunk> logChangeTask = session.getQueue().getChangeTask();
            session.getQueue().setChangeTask(new RunnableVal2<FaweChunk, FaweChunk>() {
                @Override
                public void run(FaweChunk from, FaweChunk to) {
                    if (existing != null) {
                        existing.run(from, to);
                    }
                    logChangeTask.run(from, to);
                }
            });
        }
    }
}
