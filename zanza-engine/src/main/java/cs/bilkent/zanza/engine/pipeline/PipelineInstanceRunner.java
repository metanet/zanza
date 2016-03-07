package cs.bilkent.zanza.engine.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;
import cs.bilkent.zanza.engine.coordinator.CoordinatorHandle;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstance.NO_INVOKABLE_INDEX;
import cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerCommand.PipelineInstanceRunnerCommandType;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerCommand.PipelineInstanceRunnerCommandType.PAUSE;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerCommand.PipelineInstanceRunnerCommandType.RESUME;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerCommand.PipelineInstanceRunnerCommandType.STOP;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerStatus.COMPLETED;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerStatus.INITIAL;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerStatus.PAUSED;
import static cs.bilkent.zanza.engine.pipeline.PipelineInstanceRunnerStatus.RUNNING;
import static cs.bilkent.zanza.operator.InvocationContext.InvocationReason.INPUT_PORT_CLOSED;
import cs.bilkent.zanza.operator.PortsToTuples;

public class PipelineInstanceRunner implements Runnable
{

    private static final Logger LOGGER = LoggerFactory.getLogger( PipelineInstanceRunner.class );


    private final Object monitor = new Object();

    private final PipelineInstance pipeline;

    private final PipelineInstanceId id;

    private final int operatorCount;


    private CoordinatorHandle coordinator;

    private DownstreamTupleSender downstreamTupleSender;


    private Future<Void> downstreamTuplesFuture;

    private volatile PipelineInstanceRunnerStatus status = INITIAL;

    private volatile PipelineInstanceRunnerCommand command;


    public PipelineInstanceRunner ( final PipelineInstance pipeline )
    {
        this.pipeline = pipeline;
        this.id = pipeline.id();
        this.operatorCount = pipeline.operatorCount();
    }

    public void setCoordinator ( final CoordinatorHandle coordinator )
    {
        this.coordinator = coordinator;
    }

    public void setDownstreamTupleSender ( final DownstreamTupleSender downstreamTupleSender )
    {
        this.downstreamTupleSender = downstreamTupleSender;
    }

    public PipelineInstanceRunnerStatus status ()
    {
        return status;
    }

    public CompletableFuture<Void> pause ()
    {
        final CompletableFuture<Void> result;
        synchronized ( monitor )
        {
            final PipelineInstanceRunnerStatus status = this.status;
            if ( status == PAUSED )
            {
                LOGGER.info( "{}: shortcutting pause feature since already paused", id );
                result = new CompletableFuture<>();
                result.complete( null );
            }
            else if ( status != RUNNING )
            {
                LOGGER.error( "{}: pause failed since not running! status: {}", id, status );
                result = new CompletableFuture<>();
                result.completeExceptionally( new IllegalStateException( id + ": pause failed since status: " + status ) );
            }
            else
            {
                PipelineInstanceRunnerCommand command = this.command;
                if ( command != null )
                {
                    if ( command.getType() == PAUSE )
                    {
                        LOGGER.info( "{}: shortcutting with already existing pause command", id );
                        result = command.getFuture();
                    }
                    else
                    {
                        LOGGER.error( "{}: pause failed since there is another pending command with type: {}", id, command.getType() );
                        result = new CompletableFuture<>();
                        result.completeExceptionally( new IllegalStateException( id
                                                                                 + ": pause failed since there is another pending command"
                                                                                 + " with type: " + command.getType() ) );
                    }
                }
                else
                {
                    LOGGER.info( "{}: pause command is set", id );
                    command = PipelineInstanceRunnerCommand.pause();
                    this.command = command;
                    result = command.getFuture();
                }
            }
        }

        return result;
    }

    public CompletableFuture<Void> resume ()
    {
        final CompletableFuture<Void> result;
        synchronized ( monitor )
        {
            final PipelineInstanceRunnerStatus status = this.status;
            if ( status == RUNNING )
            {
                LOGGER.info( "{}: shortcutting resume since already running", id );
                result = new CompletableFuture<>();
                result.complete( null );
            }
            else if ( status != PAUSED )
            {
                LOGGER.error( "{}: resume failed since not paused. status: {}", id, status );
                result = new CompletableFuture<>();
                result.completeExceptionally( new IllegalStateException( id + ": resume failed since not paused. status: " + status ) );
            }
            else
            {
                PipelineInstanceRunnerCommand command = this.command;
                if ( command != null )
                {
                    if ( command.getType() == RESUME )
                    {
                        LOGGER.info( "{}: resume command is already set. notifying anyway", id );
                        monitor.notify();
                        result = command.getFuture();
                    }
                    else
                    {
                        LOGGER.error( "{}: resume failed since there is another pending command with type: {}", id, command.getType() );
                        result = new CompletableFuture<>();
                        result.completeExceptionally( new IllegalStateException( id + ": resume failed since there is another pending "
                                                                                 + "command with type: " + command.getType() ) );
                    }
                }
                else
                {
                    LOGGER.info( "{}: resume command is set", id );
                    command = PipelineInstanceRunnerCommand.resume();
                    this.command = command;
                    monitor.notify();
                    result = command.getFuture();
                }
            }
        }

        return result;
    }

    public CompletableFuture<Void> stop ()
    {
        final CompletableFuture<Void> result;
        synchronized ( monitor )
        {
            final PipelineInstanceRunnerStatus status = this.status;
            if ( status == PAUSED || status == RUNNING )
            {
                PipelineInstanceRunnerCommand command = this.command;
                if ( command == null )
                {
                    LOGGER.info( "{}: stop command is set", id );
                    command = PipelineInstanceRunnerCommand.stop();
                    this.command = command;
                    result = command.getFuture();
                    monitor.notify();
                }
                else if ( command.getType() == PipelineInstanceRunnerCommandType.PAUSE || command.getType() == RESUME )
                {
                    LOGGER.info( "{}: overwriting command with type: {} to stop", id, command.getType() );
                    command.setType( STOP );
                    result = command.getFuture();
                }
                else
                {
                    LOGGER.error( "{}: stop failed since there is another pending command with type: {}", id, command.getType() );
                    result = new CompletableFuture<>();
                    result.completeExceptionally( new IllegalStateException( id
                                                                             + ": stop tail sender failed since there is another pending "
                                                                             + "command with type: " + command.getType() ) );
                }
            }
            else if ( status == COMPLETED )
            {
                result = new CompletableFuture<>();
                result.complete( null );
            }
            else
            {
                LOGGER.error( "{}: stop failed since not running or paused. status: {}", id, status );
                result = new CompletableFuture<>();
                result.completeExceptionally( new IllegalStateException( id + ": stop failed since not running or paused. status: "
                                                                         + status ) );
            }
        }

        return result;
    }

    public CompletableFuture<Void> disableInputTupleQueueBounds ()
    {
        // TODO
        // TODO should we disable per input port or all input ports at once?
        return null;
    }

    public void run ()
    {
        checkState( status == INITIAL );
        status = RUNNING;

        try
        {
            while ( true )
            {
                final int highestInvokableIndex = pipeline.currentHighestInvokableIndex();
                final PipelineInstanceRunnerStatus status = checkStatus();
                if ( status == PAUSED )
                {
                    awaitDownstreamTuplesFuture();

                    synchronized ( monitor )
                    {
                        monitor.wait( 1000 );
                    }
                    continue;
                }

                final PortsToTuples output = ( status == RUNNING ) ? pipeline.invoke() : pipeline.forceInvoke( INPUT_PORT_CLOSED );
                if ( output != null )
                {
                    awaitDownstreamTuplesFuture();
                    downstreamTuplesFuture = downstreamTupleSender.send( id, output );
                }

                final int newHighestInvokableIndex = pipeline.currentHighestInvokableIndex();
                if ( newHighestInvokableIndex == NO_INVOKABLE_INDEX || status == COMPLETED )
                {
                    completeRun();
                    break;
                }
                else if ( highestInvokableIndex == operatorCount && newHighestInvokableIndex < highestInvokableIndex )
                {
                    stopDownstream();
                }
            }
        }
        catch ( InterruptedException e )
        {
            LOGGER.error( "{}: runner thread interrupted", id );
            Thread.currentThread().interrupt();
            // TODO stop operators and clean its internal state here
        }

        if ( status == COMPLETED )
        {
            LOGGER.info( "{}: completed the run", id );
        }
        else
        {
            LOGGER.warn( "{}: completed the run with status: ", id, status );
        }
    }

    private PipelineInstanceRunnerStatus checkStatus () throws InterruptedException
    {
        PipelineInstanceRunnerStatus result = RUNNING;
        final PipelineInstanceRunnerStatus status = this.status;
        final PipelineInstanceRunnerCommand command = this.command;
        if ( command != null )
        {
            final PipelineInstanceRunnerCommandType commandType = command.getType();
            synchronized ( monitor )
            {
                if ( commandType == STOP )
                {
                    LOGGER.info( "{}: stop command is noticed", id );
                    result = COMPLETED;
                    // command is not nulled. it will be nulled while actually stopping
                }
                else if ( status == RUNNING )
                {
                    if ( commandType == PAUSE )
                    {
                        LOGGER.info( "{}: pausing", id );
                        // command must be set before status
                        command.complete();
                        this.command = null;
                        this.status = PAUSED;
                        result = PAUSED;
                    }
                    else
                    {
                        LOGGER.error( "{}: RESETTING WRONG COMMAND WITH TYPE: {} WHILE RUNNING", id, commandType );
                        command.completeExceptionally( new IllegalStateException( id + ": RESETTING WRONG COMMAND WITH TYPE: " + commandType
                                                                                  + " WHILE RUNNING" ) );
                        this.command = null;
                    }
                }
                else if ( status == PAUSED )
                {
                    if ( commandType == RESUME )
                    {
                        LOGGER.info( "{}: resuming", id );
                        // command must be set before status
                        command.complete();
                        this.command = null;
                        this.status = RUNNING;
                    }
                    else
                    {
                        LOGGER.error( "{}: RESETTING WRONG COMMAND WITH TYPE: {} WHILE PAUSED", id, commandType );
                        command.completeExceptionally( new IllegalStateException( id + ": RESETTING WRONG COMMAND WITH TYPE: " + commandType
                                                                                  + " WHILE PAUSED" ) );
                        this.command = null;
                    }
                }
            }
        }
        else
        {
            result = status;
        }

        return result;
    }

    private void awaitDownstreamTuplesFuture () throws InterruptedException
    {
        try
        {
            if ( downstreamTuplesFuture != null )
            {
                downstreamTuplesFuture.get();
                downstreamTuplesFuture = null;
            }
        }
        catch ( ExecutionException e )
        {
            LOGGER.error( id + ": await downstream tuple future failed", e );
        }
    }

    private void stopDownstream () throws InterruptedException
    {
        LOGGER.info( "{}: stopping downstream tuple sender", id );
        awaitDownstreamTuplesFuture();

        LOGGER.info( "{}: notifying coordinator to stop downstream", id );
        coordinator.notifyPipelineStoppedSendingDownstreamTuples( id );

        downstreamTupleSender = new FailingDownstreamTupleSender();
        LOGGER.info( "{}: downstream tuple sender is stopped", id );
    }

    private void completeRun () throws InterruptedException
    {
        LOGGER.info( "{}: completing the running", id );
        awaitDownstreamTuplesFuture();

        LOGGER.info( "{}: all downstream tuples are sent", id );

        final boolean notifyCoordinator = status == RUNNING;
        if ( notifyCoordinator )
        {
            LOGGER.info( "{}: notifying coordinator", id );
            coordinator.notifyPipelineCompletedRunning( id );
        }

        synchronized ( monitor )
        {
            status = COMPLETED;
            final PipelineInstanceRunnerCommand command = this.command;
            if ( command != null )
            {
                final PipelineInstanceRunnerCommandType type = command.getType();
                if ( type == RESUME || type == PAUSE )
                {
                    LOGGER.warn( "{}: completing command with type: {} exceptionally", id, type );
                    command.completeExceptionally( new IllegalStateException( id + " completed running!" ) );
                }
                else
                {
                    LOGGER.info( "{}: completing command with type: {}", id, STOP );
                    command.complete();
                }
                this.command = null;
            }
        }
    }

    private static class FailingDownstreamTupleSender implements DownstreamTupleSender
    {

        @Override
        public Future<Void> send ( final PipelineInstanceId id, final PortsToTuples tuples )
        {
            throw new UnsupportedOperationException( id + " is trying to send output tuples after stopped sending downstream tuples" );
        }

    }

}
