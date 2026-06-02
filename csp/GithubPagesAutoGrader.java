import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public class GithubPagesAutoGrader {

    private static final String CSV_FILE = "usernames.csv";
    private static final String OUTPUT_INDEX = "index.html";
    private static final String OUTPUT_TSV = "results.tsv";

    private static final String[] REQUIRED_TAGS = {
            "html", "head", "body", "h1", "h2", "p", "img", "a",
            "ul", "ol", "li", "table", "tr", "th", "td", "span",
            "div", "br", "iframe", "form", "input"
    };

    private static final String[] JS_INTERACTIVITY_KEYWORDS = {
            "onclick",
            "onchange",
            "onmouseover",
            "onmouseout",
            "addEventListener",
            "alert(",
            "prompt(",
            "confirm(",
            "document.getElementById",
            "document.querySelector",
            "innerHTML",
            "textContent",
            "classList",
            "style."
    };

    public static void main(String[] args) {
        List<Student> students = readCsv(CSV_FILE);
        List<Result> results = new ArrayList<>();

        if (students.isEmpty()) {
            System.out.println("No students found. Make sure usernames.csv exists and has Nickname,Username columns.");
            return;
        }

        for (Student student : students) {
            System.out.println("Processing " + student.nickname + " (" + student.username + ")...");

            Result result = downloadAndGrade(student);
            results.add(result);

            System.out.println("Download: " + result.downloadMessage);
            System.out.println("Score: " + String.format("%.2f", result.totalScore) + " / 30");
            System.out.println();
        }

        createIndexPage(results);
        createTsv(results);

        System.out.println("Done.");
        System.out.println("Open index.html for the full report.");
        System.out.println("Open results.tsv for spreadsheet import.");
    }

    private static Result downloadAndGrade(Student student) {
        String username = student.username;
        String repoName = username + ".github.io";

        String mainZipUrl = "https://github.com/" + username + "/" + repoName + "/archive/refs/heads/main.zip";
        String masterZipUrl = "https://github.com/" + username + "/" + repoName + "/archive/refs/heads/master.zip";

        Path tempZip = Paths.get(username + ".zip");
        Path outputFolder = Paths.get(username);

        Result result = new Result(student.nickname, username);
        result.mainUrl = mainZipUrl;
        result.masterUrl = masterZipUrl;

        try {
            deleteDirectory(outputFolder);

            DownloadResult mainResult = downloadFile(mainZipUrl, tempZip);
            result.mainStatusCode = mainResult.statusCode;
            result.mainError = mainResult.errorMessage;

            if (!mainResult.success) {
                DownloadResult masterResult = downloadFile(masterZipUrl, tempZip);
                result.masterStatusCode = masterResult.statusCode;
                result.masterError = masterResult.errorMessage;

                if (!masterResult.success) {
                    result.downloadSuccess = false;
                    result.downloadMessage = "Unable to download repository.";
                    return result;
                }
            }

            unzipRepository(tempZip, outputFolder);
            Files.deleteIfExists(tempZip);

            result.downloadSuccess = true;
            result.downloadMessage = "Repository downloaded successfully.";

        } catch (Exception e) {
            result.downloadSuccess = false;
            result.downloadMessage = "Error: " + e.getMessage();

            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException ignored) {}

            return result;
        }

        gradeStudent(result, outputFolder);
        return result;
    }

    private static void gradeStudent(Result result, Path studentFolder) {
        Path humanIndex = studentFolder.resolve("human").resolve("index.html");
        Path aiIndex = studentFolder.resolve("ai").resolve("index.html");

        result.humanIndexExists = Files.exists(humanIndex);
        result.aiIndexExists = Files.exists(aiIndex);

        result.humanWebsiteScore = result.humanIndexExists ? 10.0 : 0.0;
        result.aiWebsiteScore = result.aiIndexExists ? 10.0 : 0.0;

        if (result.humanIndexExists) {
            gradeHumanRequirements(result, humanIndex, studentFolder.resolve("human"));
        } else {
            result.requirementsMessage = "No human/index.html file found.";
        }

        result.totalScore = result.humanWebsiteScore + result.aiWebsiteScore + result.requirementsScore;
    }

    private static void gradeHumanRequirements(Result result, Path humanIndex, Path humanFolder) {
        try {
            String html = Files.readString(humanIndex, StandardCharsets.UTF_8);
            String lowerHtml = html.toLowerCase();

            for (String tag : REQUIRED_TAGS) {
                Pattern pattern = Pattern.compile("<\\s*" + tag + "\\b", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(html);

                if (matcher.find()) {
                    result.foundTags.add(tag);
                } else {
                    result.missingTags.add(tag);
                }
            }

            result.htmlTagScore = (result.foundTags.size() / (double) REQUIRED_TAGS.length) * 4.0;

            result.inlineStyleCount = countOccurrences(lowerHtml, "style=");
            result.inlineStyleScore = result.inlineStyleCount >= 3 ? 1.0 : 0.0;

            result.hasClassOrId = lowerHtml.contains("class=") || lowerHtml.contains("id=");
            result.classIdScore = result.hasClassOrId ? 1.0 : 0.0;

            result.linkedCssFiles = findLinkedCssFiles(html);
            for (String cssFile : result.linkedCssFiles) {
                Path cssPath = humanFolder.resolve(cssFile).normalize();
                if (Files.exists(cssPath) && cssPath.startsWith(humanFolder.normalize())) {
                    result.existingCssFiles.add(cssFile);
                }
            }
            result.cssScore = result.existingCssFiles.isEmpty() ? 0.0 : 2.0;

            result.linkedJsFiles = findLinkedJsFiles(html);
            for (String jsFile : result.linkedJsFiles) {
                Path jsPath = humanFolder.resolve(jsFile).normalize();

                if (Files.exists(jsPath) && jsPath.startsWith(humanFolder.normalize())) {
                    result.existingJsFiles.add(jsFile);

                    String js = Files.readString(jsPath, StandardCharsets.UTF_8);
                    for (String keyword : JS_INTERACTIVITY_KEYWORDS) {
                        if (js.contains(keyword)) {
                            result.jsInteractivityEvidence.add(keyword);
                        }
                    }
                }
            }

            result.jsLinkedScore = result.existingJsFiles.isEmpty() ? 0.0 : 1.0;
            result.jsInteractivityScore = result.jsInteractivityEvidence.isEmpty() ? 0.0 : 1.0;

            result.requirementsScore =
                    result.htmlTagScore +
                    result.cssScore +
                    result.classIdScore +
                    result.inlineStyleScore +
                    result.jsLinkedScore +
                    result.jsInteractivityScore;

            result.requirementsMessage = "Human website requirements checked.";

        } catch (IOException e) {
            result.requirementsMessage = "Could not read human/index.html: " + e.getMessage();
        }
    }

    private static List<String> findLinkedCssFiles(String html) {
        List<String> cssFiles = new ArrayList<>();

        Pattern linkPattern = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher linkMatcher = linkPattern.matcher(html);

        while (linkMatcher.find()) {
            String linkTag = linkMatcher.group();

            if (linkTag.toLowerCase().contains("stylesheet")) {
                String href = extractAttribute(linkTag, "href");

                if (href != null && !href.startsWith("http://") && !href.startsWith("https://")) {
                    cssFiles.add(cleanPath(href));
                }
            }
        }

        return cssFiles;
    }

    private static List<String> findLinkedJsFiles(String html) {
        List<String> jsFiles = new ArrayList<>();

        Pattern scriptPattern = Pattern.compile("<script\\b[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher scriptMatcher = scriptPattern.matcher(html);

        while (scriptMatcher.find()) {
            String scriptTag = scriptMatcher.group();
            String src = extractAttribute(scriptTag, "src");

            if (src != null && !src.startsWith("http://") && !src.startsWith("https://")) {
                jsFiles.add(cleanPath(src));
            }
        }

        return jsFiles;
    }

    private static String extractAttribute(String tag, String attributeName) {
        Pattern quotedPattern = Pattern.compile(attributeName + "\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher quotedMatcher = quotedPattern.matcher(tag);

        if (quotedMatcher.find()) {
            return quotedMatcher.group(1).trim();
        }

        Pattern unquotedPattern = Pattern.compile(attributeName + "\\s*=\\s*([^\\s>]+)", Pattern.CASE_INSENSITIVE);
        Matcher unquotedMatcher = unquotedPattern.matcher(tag);

        if (unquotedMatcher.find()) {
            return unquotedMatcher.group(1).trim();
        }

        return null;
    }

    private static String cleanPath(String path) {
        int questionIndex = path.indexOf("?");
        if (questionIndex >= 0) {
            path = path.substring(0, questionIndex);
        }

        int hashIndex = path.indexOf("#");
        if (hashIndex >= 0) {
            path = path.substring(0, hashIndex);
        }

        return path.trim();
    }

    private static DownloadResult downloadFile(String url, Path destination) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("User-Agent", "Java GithubPagesAutoGrader")
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int statusCode = response.statusCode();

            if (statusCode == 200) {
                Files.write(destination, response.body());
                return new DownloadResult(true, statusCode, "");
            }

            return new DownloadResult(false, statusCode, "HTTP status " + statusCode);

        } catch (Exception e) {
            return new DownloadResult(false, -1, e.getMessage());
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

                if (!outputPath.startsWith(outputFolder.normalize())) {
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
            System.out.println("Could not read " + filename + ": " + e.getMessage());
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
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
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
                    <title>GitHub Pages Auto-Grader</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            background: #f4f4f4;
                            margin: 30px;
                            color: #222;
                        }

                        h1 {
                            margin-bottom: 5px;
                        }

                        .subtitle {
                            margin-top: 0;
                            color: #555;
                        }

                        table {
                            border-collapse: collapse;
                            width: 100%;
                            background: white;
                            box-shadow: 0 2px 8px rgba(0,0,0,0.15);
                            margin-top: 20px;
                        }

                        th, td {
                            padding: 10px;
                            border-bottom: 1px solid #ddd;
                            text-align: left;
                            vertical-align: top;
                        }

                        th {
                            background: #222;
                            color: white;
                            position: sticky;
                            top: 0;
                        }

                        tr:hover {
                            background: #f9f9f9;
                        }

                        .score {
                            font-weight: bold;
                            font-size: 1.1em;
                        }

                        .good {
                            color: #137333;
                            font-weight: bold;
                        }

                        .bad {
                            color: #b3261e;
                            font-weight: bold;
                        }

                        .warn {
                            color: #b06000;
                            font-weight: bold;
                        }

                        .small {
                            font-size: 0.85em;
                            word-break: break-word;
                        }

                        details {
                            margin-top: 8px;
                            background: #fafafa;
                            border: 1px solid #ddd;
                            border-radius: 6px;
                            padding: 8px;
                        }

                        summary {
                            cursor: pointer;
                            font-weight: bold;
                        }

                        .pill {
                            display: inline-block;
                            padding: 2px 6px;
                            margin: 2px;
                            border-radius: 999px;
                            background: #e8f0fe;
                            font-size: 0.85em;
                        }

                        .missing {
                            background: #fce8e6;
                        }

                        code {
                            background: #eee;
                            padding: 1px 4px;
                            border-radius: 4px;
                        }

                        a {
                            color: #0645ad;
                        }
                    </style>
                </head>
                <body>
                    <h1>GitHub Pages Auto-Grader</h1>
                    <p class="subtitle">30 point scale: human site 10 pts, AI site 10 pts, human requirements 10 pts.</p>

                    <table>
                        <tr>
                            <th>Student</th>
                            <th>Download</th>
                            <th>Human Site<br>10 pts</th>
                            <th>AI Site<br>10 pts</th>
                            <th>Requirements<br>10 pts</th>
                            <th>Total<br>30 pts</th>
                            <th>Details</th>
                        </tr>
                """);

        for (Result r : results) {
            html.append("<tr>");
            
            html.append("<td><strong>")
                    .append(escapeHtml(r.nickname))
                    .append("</strong><br><span class=\"small\">")
                    .append(escapeHtml(r.username))
                    .append("</span><br><a href=\"")
                    .append(escapeHtml(r.humanSiteUrl))
                    .append("\" target=\"_blank\">Open human site</a></td>");
                    .append(escapeHtml(r.aiSiteUrl))
                    .append("\" target=\"_blank\">Open ai site</a></td>");

            html.append("<td>")
                    .append(r.downloadSuccess ? "<span class=\"good\">Downloaded</span>" : "<span class=\"bad\">Failed</span>")
                    .append("<br><span class=\"small\">")
                    .append(escapeHtml(r.downloadMessage))
                    .append("</span></td>");

            html.append("<td>")
                    .append(formatStatus(r.humanIndexExists))
                    .append("<br>")
                    .append(formatScore(r.humanWebsiteScore))
                    .append("</td>");

            html.append("<td>")
                    .append(formatStatus(r.aiIndexExists))
                    .append("<br>")
                    .append(formatScore(r.aiWebsiteScore))
                    .append("</td>");

            html.append("<td>")
                    .append(formatScore(r.requirementsScore))
                    .append("<br><span class=\"small\">")
                    .append(escapeHtml(r.requirementsMessage))
                    .append("</span></td>");

            html.append("<td class=\"score\">")
                    .append(String.format("%.2f", r.totalScore))
                    .append("</td>");

            html.append("<td>");
            appendDetails(html, r);
            html.append("</td>");

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
            System.out.println("Could not create index.html: " + e.getMessage());
        }
    }

    private static void appendDetails(StringBuilder html, Result r) {
        html.append("<details>");
        html.append("<summary>Open grading details</summary>");

        html.append("<h4>Download URLs</h4>");
        html.append("<p class=\"small\"><strong>Main:</strong> <a href=\"")
                .append(escapeHtml(r.mainUrl))
                .append("\">")
                .append(escapeHtml(r.mainUrl))
                .append("</a><br>Status: ")
                .append(r.mainStatusCode)
                .append("<br>Error: ")
                .append(escapeHtml(r.mainError))
                .append("</p>");

        html.append("<p class=\"small\"><strong>Master:</strong> <a href=\"")
                .append(escapeHtml(r.masterUrl))
                .append("\">")
                .append(escapeHtml(r.masterUrl))
                .append("</a><br>Status: ")
                .append(r.masterStatusCode)
                .append("<br>Error: ")
                .append(escapeHtml(r.masterError))
                .append("</p>");

        html.append("<h4>Human Website Requirements</h4>");
        html.append("<ul>");
        html.append("<li>Required HTML tags: ")
                .append(r.foundTags.size())
                .append("/")
                .append(REQUIRED_TAGS.length)
                .append(" — ")
                .append(formatScore(r.htmlTagScore))
                .append(" / 4</li>");

        html.append("<li>External CSS file linked and found: ")
                .append(r.existingCssFiles.isEmpty() ? "<span class=\"bad\">No</span>" : "<span class=\"good\">Yes</span>")
                .append(" — ")
                .append(formatScore(r.cssScore))
                .append(" / 2</li>");

        html.append("<li>Class or ID used: ")
                .append(r.hasClassOrId ? "<span class=\"good\">Yes</span>" : "<span class=\"bad\">No</span>")
                .append(" — ")
                .append(formatScore(r.classIdScore))
                .append(" / 1</li>");

        html.append("<li>Inline style attributes: ")
                .append(r.inlineStyleCount)
                .append(" / 3 required — ")
                .append(formatScore(r.inlineStyleScore))
                .append(" / 1</li>");

        html.append("<li>External JS file linked and found: ")
                .append(r.existingJsFiles.isEmpty() ? "<span class=\"bad\">No</span>" : "<span class=\"good\">Yes</span>")
                .append(" — ")
                .append(formatScore(r.jsLinkedScore))
                .append(" / 1</li>");

        html.append("<li>JavaScript interactivity evidence: ")
                .append(r.jsInteractivityEvidence.isEmpty() ? "<span class=\"bad\">No</span>" : "<span class=\"good\">Yes</span>")
                .append(" — ")
                .append(formatScore(r.jsInteractivityScore))
                .append(" / 1</li>");

        html.append("</ul>");

        html.append("<h4>Found Tags</h4>");
        for (String tag : r.foundTags) {
            html.append("<span class=\"pill\">&lt;").append(escapeHtml(tag)).append("&gt;</span>");
        }

        html.append("<h4>Missing Tags</h4>");
        if (r.missingTags.isEmpty()) {
            html.append("<span class=\"good\">None</span>");
        } else {
            for (String tag : r.missingTags) {
                html.append("<span class=\"pill missing\">&lt;").append(escapeHtml(tag)).append("&gt;</span>");
            }
        }

        html.append("<h4>Linked CSS Files</h4>");
        html.append("<p class=\"small\">Referenced: ")
                .append(escapeHtml(r.linkedCssFiles.toString()))
                .append("<br>Found: ")
                .append(escapeHtml(r.existingCssFiles.toString()))
                .append("</p>");

        html.append("<h4>Linked JS Files</h4>");
        html.append("<p class=\"small\">Referenced: ")
                .append(escapeHtml(r.linkedJsFiles.toString()))
                .append("<br>Found: ")
                .append(escapeHtml(r.existingJsFiles.toString()))
                .append("<br>Interactivity evidence: ")
                .append(escapeHtml(r.jsInteractivityEvidence.toString()))
                .append("</p>");

        html.append("</details>");
    }

    private static void createTsv(List<Result> results) {
        StringBuilder tsv = new StringBuilder();

        tsv.append("Nickname\tUsername\tDownloadSuccess\tHumanScore\tAIScore\tRequirementsScore\tTotalScore\tMissingTags\tMessage\n");

        for (Result r : results) {
            tsv.append(cleanTsv(r.nickname)).append("\t")
                    .append(cleanTsv(r.username)).append("\t")
                    .append(r.downloadSuccess).append("\t")
                    .append(String.format("%.2f", r.humanWebsiteScore)).append("\t")
                    .append(String.format("%.2f", r.aiWebsiteScore)).append("\t")
                    .append(String.format("%.2f", r.requirementsScore)).append("\t")
                    .append(String.format("%.2f", r.totalScore)).append("\t")
                    .append(cleanTsv(r.missingTags.toString())).append("\t")
                    .append(cleanTsv(r.downloadMessage + " " + r.requirementsMessage)).append("\n");
        }

        try {
            Files.writeString(Paths.get(OUTPUT_TSV), tsv.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Could not create results.tsv: " + e.getMessage());
        }
    }

    private static String formatStatus(boolean exists) {
        return exists ? "<span class=\"good\">Found</span>" : "<span class=\"bad\">Missing</span>";
    }

    private static String formatScore(double score) {
        return String.format("%.2f", score);
    }

    private static String cleanTsv(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }

        return count;
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

    private static class DownloadResult {
        boolean success;
        int statusCode;
        String errorMessage;

        DownloadResult(boolean success, int statusCode, String errorMessage) {
            this.success = success;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }
    }

    private static class Result {
        String nickname;
        String username;
        String liveSiteUrl = "";

        boolean downloadSuccess = false;
        String downloadMessage = "";

        String mainUrl = "";
        String masterUrl = "";
        int mainStatusCode = -1;
        int masterStatusCode = -1;
        String mainError = "";
        String masterError = "";

        boolean humanIndexExists = false;
        boolean aiIndexExists = false;

        double humanWebsiteScore = 0;
        double aiWebsiteScore = 0;
        double requirementsScore = 0;
        double totalScore = 0;

        double htmlTagScore = 0;
        double cssScore = 0;
        double classIdScore = 0;
        double inlineStyleScore = 0;
        double jsLinkedScore = 0;
        double jsInteractivityScore = 0;

        int inlineStyleCount = 0;
        boolean hasClassOrId = false;

        String requirementsMessage = "";

        List<String> foundTags = new ArrayList<>();
        List<String> missingTags = new ArrayList<>();

        List<String> linkedCssFiles = new ArrayList<>();
        List<String> existingCssFiles = new ArrayList<>();

        List<String> linkedJsFiles = new ArrayList<>();
        List<String> existingJsFiles = new ArrayList<>();

        List<String> jsInteractivityEvidence = new ArrayList<>();

        Result(String nickname, String username) {
             this.nickname = nickname;
             this.username = username;
             this.humanSiteUrl = "https://ballardcs.krokower.com/csp/" + username + "/human";

             this.aiSiteUrl = "https://ballardcs.krokower.com/csp/" + username + "/ai";
         }
    }
}