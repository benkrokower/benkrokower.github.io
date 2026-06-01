// GithubPagesDownloader.java
// Compile: javac GithubPagesDownloader.java
// Run:     java GithubPagesDownloader

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class GithubPagesDownloader {

    private static final String CSV_FILE = "usernames.csv";
    private static final String OUTPUT_INDEX = "index.html";

    public static void main(String[] args) {
        List<Student> students = readCsv(CSV_FILE);
        List<Result> results = new ArrayList<>();

        for (Student student : students) {
            System.out.println("Downloading " + student.username + "...");

            Result result = downloadRepository(student);
            results.add(result);

            System.out.println(result.message);
            System.out.println("Main URL: " + result.mainUrl + " Status: " + result.mainStatusCode);
            System.out.println("Master URL: " + result.masterUrl + " Status: " + result.masterStatusCode);
            System.out.println();
        }

        createIndexPage(results);
        System.out.println("Done. Open index.html to view results.");
    }

    private static Result downloadRepository(Student student) {
        String username = student.username;
        String repoName = username + ".github.io";

        String mainZipUrl = "https://github.com/" + username + "/" + repoName + "/archive/refs/heads/main.zip";
        String masterZipUrl = "https://github.com/" + username + "/" + repoName + "/archive/refs/heads/master.zip";

        Path tempZip = Paths.get(username + ".zip");
        Path outputFolder = Paths.get(username);

        int mainStatus = -1;
        int masterStatus = -1;

        try {
            deleteDirectory(outputFolder);

            DownloadResult mainResult = downloadFile(mainZipUrl, tempZip);
            mainStatus = mainResult.statusCode;

            if (!mainResult.success) {
                DownloadResult masterResult = downloadFile(masterZipUrl, tempZip);
                masterStatus = masterResult.statusCode;

                if (!masterResult.success) {
                    return new Result(
                            student.nickname,
                            username,
                            false,
                            mainZipUrl,
                            masterZipUrl,
                            mainStatus,
                            masterStatus,
                            "Unable to download repository"
                    );
                }
            }

            unzipRepository(tempZip, outputFolder);
            Files.deleteIfExists(tempZip);

            if (!Files.exists(outputFolder.resolve("index.html"))) {
                return new Result(
                        student.nickname,
                        username,
                        false,
                        mainZipUrl,
                        masterZipUrl,
                        mainStatus,
                        masterStatus,
                        "Downloaded repository, but no index.html was found"
                );
            }

            return new Result(
                    student.nickname,
                    username,
                    true,
                    mainZipUrl,
                    masterZipUrl,
                    mainStatus,
                    masterStatus,
                    "Downloaded successfully"
            );

        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException ignored) {}

            return new Result(
                    student.nickname,
                    username,
                    false,
                    mainZipUrl,
                    masterZipUrl,
                    mainStatus,
                    masterStatus,
                    "Error: " + e.getMessage()
            );
        }
    }

    private static DownloadResult downloadFile(String url, Path destination) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int statusCode = response.statusCode();

            if (statusCode == 200) {
                Files.write(destination, response.body());
                return new DownloadResult(true, statusCode);
            }

            return new DownloadResult(false, statusCode);

        } catch (Exception e) {
            return new DownloadResult(false, -1);
        }
    }

    private static void unzipRepository(Path zipFile, Path outputFolder) throws IOException {
        Files.createDirectories(outputFolder);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                int firstSlash = entryName.indexOf("/");
                if (firstSlash == -1) {
                    continue;
                }

                String relativePath = entryName.substring(firstSlash + 1);

                if (relativePath.isEmpty()) {
                    continue;
                }

                Path outputPath = outputFolder.resolve(relativePath).normalize();

                if (!outputPath.startsWith(outputFolder)) {
                    throw new IOException("Unsafe zip entry: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    private static List<Student> readCsv(String filename) {
        List<Student> students = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename), StandardCharsets.UTF_8)) {
            String header = reader.readLine();

            if (header == null) {
                return students;
            }

            String line;

            while ((line = reader.readLine()) != null) {
                List<String> parts = parseCsvLine(line);

                if (parts.size() >= 2) {
                    String nickname = parts.get(0).trim();
                    String username = parts.get(1).trim();

                    if (!nickname.isEmpty() && !username.isEmpty()) {
                        students.add(new Student(nickname, username));
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Could not read " + filename);
        }

        return students;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                insideQuotes = !insideQuotes;
            } else if (c == ',' && !insideQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString());
        return values;
    }

    private static void createIndexPage(List<Result> results) {
        StringBuilder html = new StringBuilder();

        html.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Student GitHub Pages</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            background: #f4f4f4;
                            margin: 40px;
                        }

                        h1 {
                            color: #222;
                        }

                        table {
                            border-collapse: collapse;
                            width: 100%;
                            background: white;
                            box-shadow: 0 2px 8px rgba(0,0,0,0.15);
                        }

                        th, td {
                            padding: 12px;
                            border-bottom: 1px solid #ddd;
                            text-align: left;
                            vertical-align: top;
                        }

                        th {
                            background: #222;
                            color: white;
                        }

                        tr:hover {
                            background: #f1f1f1;
                        }

                        .success {
                            color: green;
                            font-weight: bold;
                        }

                        .failed {
                            color: red;
                            font-weight: bold;
                        }

                        a {
                            color: #0066cc;
                            text-decoration: none;
                        }

                        a:hover {
                            text-decoration: underline;
                        }

                        .small {
                            font-size: 0.85em;
                            word-break: break-all;
                        }
                    </style>
                </head>
                <body>
                    <h1>Student GitHub Pages</h1>
                    <table>
                        <tr>
                            <th>Nickname</th>
                            <th>Username</th>
                            <th>Status</th>
                            <th>Local Link</th>
                            <th>Main Download URL</th>
                            <th>Main Status</th>
                            <th>Master Download URL</th>
                            <th>Master Status</th>
                            <th>Message</th>
                        </tr>
                """);

        for (Result result : results) {
            html.append("<tr>");

            html.append("<td>").append(escapeHtml(result.nickname)).append("</td>");
            html.append("<td>").append(escapeHtml(result.username)).append("</td>");

            if (result.success) {
                html.append("<td class=\"success\">Downloaded</td>");
                html.append("<td><a href=\"")
                        .append(escapeHtml(result.username))
                        .append("/index.html\">Open site</a></td>");
            } else {
                html.append("<td class=\"failed\">Unable to download repository</td>");
                html.append("<td></td>");
            }

            html.append("<td class=\"small\"><a href=\"")
                    .append(escapeHtml(result.mainUrl))
                    .append("\">main.zip</a><br>")
                    .append(escapeHtml(result.mainUrl))
                    .append("</td>");

            html.append("<td>").append(result.mainStatusCode).append("</td>");

            html.append("<td class=\"small\"><a href=\"")
                    .append(escapeHtml(result.masterUrl))
                    .append("\">master.zip</a><br>")
                    .append(escapeHtml(result.masterUrl))
                    .append("</td>");

            html.append("<td>").append(result.masterStatusCode).append("</td>");

            html.append("<td>").append(escapeHtml(result.message)).append("</td>");

            html.append("</tr>\n");
        }

        html.append("""
                    </table>
                </body>
                </html>
                """);

        try {
            Files.writeString(Paths.get(OUTPUT_INDEX), html.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Could not create index.html");
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {}
                });
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static class Student {
        String nickname;
        String username;

        Student(String nickname, String username) {
            this.nickname = nickname;
            this.username = username;
        }
    }

    private static class Result {
        String nickname;
        String username;
        boolean success;
        String mainUrl;
        String masterUrl;
        int mainStatusCode;
        int masterStatusCode;
        String message;

        Result(String nickname, String username, boolean success, String mainUrl, String masterUrl,
               int mainStatusCode, int masterStatusCode, String message) {
            this.nickname = nickname;
            this.username = username;
            this.success = success;
            this.mainUrl = mainUrl;
            this.masterUrl = masterUrl;
            this.mainStatusCode = mainStatusCode;
            this.masterStatusCode = masterStatusCode;
            this.message = message;
        }
    }

    private static class DownloadResult {
        boolean success;
        int statusCode;

        DownloadResult(boolean success, int statusCode) {
            this.success = success;
            this.statusCode = statusCode;
        }
    }
}