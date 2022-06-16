package io.mamish.therealobama;

import org.javacord.api.DiscordApi;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public class InteractionHandler implements SlashCommandCreateListener {

    private static final long COMMAND_TIMEOUT_SECONDS = 60;
    private static final String COMMAND_NAME = "obamasay";
    private static final String COMMAND_DESCRIPTION = "Have the real Obama say something in your voice channel";
    private static final String SCRIPT_ARG_NAME = "script";
    private static final String SCRIPT_ARG_DESCRIPTION = "The full script for Obama to say";

    private final ExecutorService workflowThreadPool = Executors.newCachedThreadPool();
    private final WordLoader wordLoader = new WordLoader();

    public void putSlashCommands(DiscordApi discordApi) {
        SlashCommand.with(COMMAND_NAME, COMMAND_DESCRIPTION, List.of(SlashCommandOption.create(
                SlashCommandOptionType.STRING,
                SCRIPT_ARG_NAME,
                SCRIPT_ARG_DESCRIPTION,
                true
        ))).createGlobal(discordApi).join();
    }

    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent event) {
        var slashCommandInteraction = event.getInteraction().asSlashCommandInteraction().orElseThrow();
        if (slashCommandInteraction.getCommandName().equals(COMMAND_NAME)) {
            String script = event.getSlashCommandInteraction().getOptionByIndex(0).flatMap(SlashCommandInteractionOption::getStringValue).orElseThrow();
            handleObamaSayCommand(slashCommandInteraction, script);
        } else {
            handleUnknownCommand(slashCommandInteraction);
        }
    }

    private void handleObamaSayCommand(SlashCommandInteraction command, String script) {
        InteractionMessageUpdater messageUpdater = new InteractionMessageUpdater(command);
        var maybeUserVoiceChannel = command.getUser().getConnectedVoiceChannels().stream().findAny();
        if (maybeUserVoiceChannel.isEmpty()) {
            messageUpdater.println("Obama can't speak unless you're in a voice channel");
            return;
        }

        var transcriptWords = tokenizeScript(script);

        if (transcriptWords.isEmpty()) {
            messageUpdater.println("Obama can't work with an empty script");
            return;
        }

        SpeakWorkflowInstance workflowInstance = new SpeakWorkflowInstance(command, messageUpdater, maybeUserVoiceChannel.get(), transcriptWords, wordLoader);
        CompletableFuture<Void> workflowFuture = CompletableFuture.runAsync(workflowInstance);
        CompletableFuture.runAsync(() -> {
            try {
                workflowFuture.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                messageUpdater.println("Obama was unexpectedly interrupted");
            } catch (ExecutionException e) {
                messageUpdater.println("Obama ran into a problem, apparently '" + e.getCause().getMessage() + "'");
            } catch (TimeoutException e) {
                messageUpdater.println("Obama has run out of time to give his speech");
            }
        });
    }

    private void handleUnknownCommand(SlashCommandInteraction command) {
        new InteractionMessageUpdater(command).println("This isn't supposed to happen - I don't recognise that command");
    }

    private List<String> tokenizeScript(String script) {
        return Arrays.stream(script.split("[^A-Za-z\\d]+"))
                .filter(not(String::isBlank))
                .collect(Collectors.toList());
    }
}
