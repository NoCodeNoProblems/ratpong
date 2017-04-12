package pl.setblack.pongi.games;

import com.fasterxml.jackson.core.type.TypeReference;
import javaslang.collection.List;
import org.junit.jupiter.api.Test;
import pl.setblack.pongi.JSONMapping;
import pl.setblack.pongi.Server;
import pl.setblack.pongi.games.api.GameInfo;
import pl.setblack.pongi.games.api.GameState;
import pl.setblack.pongi.games.repo.GamesRepositoryInMemory;
import pl.setblack.pongi.scores.ScoresModule;
import pl.setblack.pongi.scores.repo.ScoresRepositoryInMem;
import pl.setblack.pongi.scores.repo.ScoresRepositoryProcessor;
import pl.setblack.pongi.users.api.Session;
import pl.setblack.pongi.users.repo.SessionsRepo;
import ratpack.http.client.ReceivedResponse;
import ratpack.test.embed.EmbeddedApp;
import ratpack.test.http.TestHttpClient;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class GamesServiceTest {

    private final Clock clock  = Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), ZoneId.of("GMT"));
    private final SessionsRepo sessionsRepo = new SessionsRepo(clock);

    private final Session user1 = sessionsRepo.startSession("user1");
    private final Session user2 = sessionsRepo.startSession("user2");

    @Test
    public void canCreateGame() throws Exception{
        prepareServer().test(
                testHttpClient -> {
                    postHttp(testHttpClient, "/api/games/games", "game1", user1);
                    final String response = getHttp(testHttpClient, "/api/games/games");
                    final List<GameInfo> games = JSONMapping.getJsonMapping().readerFor( new TypeReference<List<GameInfo>>(){}).readValue(response);
                    assertEquals("game1", games.get(0).name);
                }
        );
    }


    @Test
    public void user2CanJoinGame() throws Exception{
        prepareServer().test(
                testHttpClient -> {
                    final GameInfo game = JSONMapping.getJsonMapping().readerFor(GameInfo.class).readValue(
                            postHttp(testHttpClient, "/api/games/games", "game1", user1)
                    );
                    final GameState game2 = JSONMapping.getJsonMapping().readerFor(GameState.class).readValue(
                            postHttp(testHttpClient, "/api/games/game/"+game.uuid, "game1", user2)
                    );

                    assertTrue(game2.players._2.name.equals("user2"));
                }
        );
    }

    private String postHttp(TestHttpClient testHttpClient, String url, String content, Session session) {
        return testHttpClient.requestSpec(rs ->
                                rs.headers( mh ->
                                        mh
                                                .add("Content-type", "application/json")
                                                .add("Authorization", "Bearer "+ session.uuid)
                                        )
                                        .body( body -> body.text(content)))
                                .post(url)
                                .getBody().getText();
    }

    private String getHttp(TestHttpClient testHttpClient, String url) {
        return testHttpClient
                .get(url)
                .getBody().getText();
    }


    private GamesService createGameService() {

        final GamesRepositoryInMemory gamesRepo = new GamesRepositoryInMemory(clock);

        final ScoresRepositoryProcessor scoresProcs = new ScoresModule(new ScoresRepositoryInMem()).getScoresRepository();
        final GamesService gamesService = new GamesService(gamesRepo, sessionsRepo, scoresProcs);
        return gamesService;

    }


    private EmbeddedApp prepareServer() {
        final GamesService gamesService = createGameService();
        return EmbeddedApp.fromServer(
                Server.createUnconfiguredServer(gamesService.gamesApi())
        );
    }

}