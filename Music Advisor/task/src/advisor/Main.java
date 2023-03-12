package advisor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.net.*;
import java.net.http.HttpResponse;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static String accessPoint = "https://accounts.spotify.com";
    private static String resourceApi = "https://api.spotify.com";
    private static int songsPerPage = 5;
    private static final Scanner scanner = new Scanner(System.in);
    private static final int port = (int)(Math.random()*20) + 8080;
    private static final String[] query = new String[1];

    private static HttpServer server;
    private static String accessToken = "";
    private static final HttpClient client = HttpClient.newBuilder().build();
    private static String categoriesUri;
    private static String featuredUri;
    private static String newReleasesUri;
    private static String playlistsUri;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 6) {
            if (args[0].equals("-access")) {
                accessPoint = args[1];
            }

            if (args[2].equals("-resource")) {
                resourceApi = args[3];
            }

            if (args[4].equals("-page")) {
                songsPerPage = Integer.parseInt(args[5]);
            }
        }

        categoriesUri = resourceApi + "/v1/browse/categories";
        featuredUri = resourceApi + "/v1/browse/featured-playlists";
        newReleasesUri = resourceApi + "/v1/browse/new-releases";
        playlistsUri = resourceApi + "/v1/browse/categories/%s/playlists";

        String answer = scanner.nextLine();
        setupServer();

        boolean authorized = false;

        while (!"exit".equals(answer)) {
            StringBuilder[] pages = null;

            if (!authorized) {
                if ("auth".equals(answer)) {
                    server.start();
                    authorize();
                    server.stop(1);
                    authorized = true;
                } else {
                    System.out.println("Please, provide access for application.");
                }
            } else if ("new".equals(answer)) {
                pages = new_releases();
                answer = null;
            } else if ("featured".equals(answer)) {
                pages = featured_playlists();
                answer = null;
            } else if ("categories".equals(answer)) {
                pages = categories();
                answer = null;
            } else if (answer.startsWith("playlists ")) {
                String category = answer.replace("playlists ", "");
                pages = playlistsByCategory(category);
                answer = null;
            }

            if (pages != null) {
                answer = viewPages(pages);
            }

            if (answer != null) {
                answer = scanner.nextLine();
            }
        }
        server.stop(0);
        System.out.println("---GOODBYE!---");
    }

    /**
     * Fetches the new releases from the Spotify API
     */
    private static StringBuilder[] new_releases() throws IOException, InterruptedException {
        HttpResponse<String> response = getResponse(newReleasesUri);

        JsonArray newReleases = JsonParser.parseString(response.body()).getAsJsonObject().get("albums").getAsJsonObject().get("items").getAsJsonArray();

        int pageCount = newReleases.size() / songsPerPage;

        StringBuilder[] pages = new StringBuilder[pageCount];

        int index = 0;

        while (index < newReleases.size()) {
            for (int i = 0; i < pageCount; i++) {
                for (int j = 0; j < songsPerPage; j++) {
                    if (pages[i] == null) {
                        pages[i] = new StringBuilder();
                    }
                    if (index < newReleases.size()) {
                        pages[i].append(newReleases.get(index).getAsJsonObject().get("name").getAsString()).append("\n");

                        // Add the artists
                        JsonArray artists = newReleases.get(i).getAsJsonObject().get("artists").getAsJsonArray();
                        List<String> artistNames = new ArrayList<>();
                        for (int k = 0; k < artists.size(); k++) {
                            artistNames.add(artists.get(k).getAsJsonObject().get("name").getAsString());
                        }

                        pages[i].append("[").append(String.join(", ", artistNames)).append("]\n");

                        // Add the external URL
                        pages[i].append(newReleases.get(index).getAsJsonObject().get("external_urls").getAsJsonObject().get("spotify").getAsString()).append("\n\n");
                        index++;
                    }
                }
            }
        }

        return pages;
    }

    /**
     * Prints pages to the console. When the user enter next or prev, the next or previous page is printed.
     * @param pages - the pages to print
     */
    private static String viewPages(StringBuilder[] pages) {
        int index = 0;
        viewPage(pages[index], index, pages.length);

        String answer = scanner.nextLine();
        while (true) {
            if ("next".equals(answer)) {
                if (index >= pages.length - 1) {
                    System.out.println("No more pages.");
                    answer = scanner.nextLine();
                    continue;
                }
                index++;
            } else if ("prev".equals(answer)) {
                if (index == 0) {
                    System.out.println("No more pages.");
                    answer = scanner.nextLine();
                    continue;
                }
                index--;
            } else {
                break;
            }

            viewPage(pages[index], index, pages.length);

            answer = scanner.nextLine();
        }

        return answer;
    }

    /**
     * Print page to the console
     * @param page - the page to print
     */
    private static void viewPage(StringBuilder page, int index, int pageCount) {
        System.out.println(page);
        System.out.println("---PAGE " + (index + 1) + " OF " + pageCount + "---");
    }

    /**
     * Prints the categories from the Spotify API
     * @throws IOException if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    private static StringBuilder[] categories() throws IOException, InterruptedException {
        JsonArray categories = getCategories();

        int pageCount = categories.size() / songsPerPage;

        StringBuilder[] pages = new StringBuilder[pageCount];

        for (int i = 0; i < pageCount; i++) {
            pages[i] = new StringBuilder();

            for (int j = 0; j < songsPerPage; j++) {
                if (i * songsPerPage + j >= categories.size()) {
                    break;
                }
                pages[i].append(categories.get(i * songsPerPage + j).getAsJsonObject().get("name").getAsString()).append("\n");
            }
        }

        return pages;
    }

    /**
     * Fetches the categories from the Spotify API
     * @return HttpResponse<String> - the response from the Spotify API
     * @throws IOException if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    private static JsonArray getCategories() throws IOException, InterruptedException {
        HttpResponse<String> response = getResponse(categoriesUri);

        return JsonParser.parseString(response.body()).getAsJsonObject().get("categories").getAsJsonObject().get("items").getAsJsonArray();
    }

    /**
     * Fetches the featured playlists from the Spotify API
     * @throws IOException if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    private static StringBuilder[] featured_playlists() throws IOException, InterruptedException {
        HttpResponse<String> response = getResponse(featuredUri);

        JsonArray playlists = JsonParser.parseString(response.body()).getAsJsonObject().get("playlists").getAsJsonObject().get("items").getAsJsonArray();

        int pageCount = playlists.size() / songsPerPage;

        StringBuilder[] pages = new StringBuilder[pageCount];

        for (int i = 0; i < pageCount; i++) {
            pages[i] = new StringBuilder();

            for (int j = 0; j < songsPerPage; j++) {
                if (i * songsPerPage + j >= playlists.size()) {
                    break;
                }
                pages[i].append(playlists.get(i * songsPerPage + j).getAsJsonObject().get("name").getAsString()).append("\n");
                pages[i].append(playlists.get(i * songsPerPage + j).getAsJsonObject().get("external_urls").getAsJsonObject().get("spotify").getAsString()).append("\n\n");
            }
        }

        return pages;
    }

    /**
     * Fetches the playlists for a given category from the Spotify API
     * @param category the category to fetch playlists for
     * @throws IOException if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    private static StringBuilder[] playlistsByCategory(String category) throws IOException, InterruptedException {
        JsonArray categories = getCategories();

        String categoryId = null;
        for (int i = 0; i < categories.size(); i++) {
            if (category.equals(categories.get(i).getAsJsonObject().get("name").getAsString())) {
                categoryId = categories.get(i).getAsJsonObject().get("id").getAsString();
                break;
            }
        }

        if (categoryId == null) {
            System.out.println("Unknown category name.");
            return null;
        }
        HttpResponse<String> response = getResponse(playlistsUri.formatted(categoryId));

        JsonElement error = JsonParser.parseString(response.body()).getAsJsonObject().get("error");
        if (error != null) {
            System.out.println(error.getAsJsonObject().get("message").getAsString());
            return null;
        }

        JsonArray playlists = JsonParser.parseString(response.body()).getAsJsonObject().get("playlists").getAsJsonObject().get("items").getAsJsonArray();

        int pageCount = playlists.size() / songsPerPage;

        StringBuilder[] pages = new StringBuilder[pageCount];

        for (int i = 0; i < pageCount; i++) {
            pages[i] = new StringBuilder();

            for (int j = 0; j < songsPerPage; j++) {
                if (i * songsPerPage + j >= playlists.size()) {
                    break;
                }
                pages[i].append(playlists.get(i * songsPerPage + j).getAsJsonObject().get("name").getAsString()).append("\n");
                pages[i].append(playlists.get(i * songsPerPage + j).getAsJsonObject().get("external_urls").getAsJsonObject().get("spotify").getAsString()).append("\n\n");
            }
        }

        return pages;
    }

    /**
     * Send a request to the Spotify API and return the response
     *
     * @param url the URL to send the request to
     * @return HttpResponse<String> - the response from the Spotify API
     * @throws IOException if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    private static HttpResponse<String> getResponse(String url) throws IOException, InterruptedException {
        return getResponse(url, "Bearer %s".formatted(accessToken));
    }

    /**
     * Send a request to the Spotify API and return the response
     *
     * @param url                 the URL to send the request to
     * @param authorizationString the authorization string to send with the request
     * @return HttpResponse<String> - the response from the Spotify API
     * @throws IOException          if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    private static HttpResponse<String> getResponse(String url, String authorizationString) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .header("Authorization", authorizationString)
                .uri(URI.create(url))
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void setupServer() throws IOException {
        server = HttpServer.create();

        try {
            server.bind(new InetSocketAddress(port), 20);
        } catch (BindException e) {
            e.printStackTrace();
        }

        query[0] = "";
        server.createContext("/",
                exchange -> {
                    String responseSuccessContent = "Got the code. Return back to your program.";
                    String responseFailedContent = "Authorization code not found. Try again.";
                    String queryResult = exchange.getRequestURI().getQuery();

                    if (queryResult != null && queryResult.startsWith("code=")) {
                        exchange.sendResponseHeaders(200, responseSuccessContent.length());
                        exchange.getResponseBody().write(responseSuccessContent.getBytes());
                        query[0] = queryResult;
                    } else {
                        exchange.sendResponseHeaders(200, responseFailedContent.length());
                        exchange.getResponseBody().write(responseFailedContent.getBytes());
                    }
                    exchange.getResponseBody().close();
                }
        );
    }

    private static void authorize() throws IOException, InterruptedException {
        System.out.println("use this link to request the access code:");
        System.out.println("%s/authorize?".formatted(accessPoint) +
                "client_id=8191c51896a747d2be943eb5e7cf45c7" +
                "&redirect_uri=http://localhost:%d&response_type=code".formatted(port));

        System.out.println("waiting for code...");

        while (query[0].equals("")) {
            Thread.sleep(10);
        }

        String accessCode = query[0];

        server.stop(0);

        if (accessCode.startsWith("code=")) {
            System.out.println("code received");
        } else {
            System.out.println("Authorization code not found. Try again.");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic ODE5MWM1MTg5NmE3NDdkMmJlOTQzZWI1ZTdjZjQ1Yzc6MmY4NTg1N2JmYTk1NDY5YmJlNjc0OGNlNmNmMDI0Mjg=")
                .uri(URI.create("%s/api/token".formatted(accessPoint)))
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&%s&redirect_uri=http://localhost:%d".formatted(accessCode, port)))
                .build();

        System.out.println("Making http request for access_token...");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        accessToken = JsonParser.parseString(response.body()).getAsJsonObject().get("access_token").getAsString();

        System.out.println("Success!");
    }
}
