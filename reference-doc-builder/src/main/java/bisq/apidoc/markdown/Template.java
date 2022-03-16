/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */
package bisq.apidoc.markdown;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static bisq.apidoc.protobuf.ProtoParserUtil.toText;
import static java.lang.String.format;

/**
 * Loads a template MD file from the resources folder.
 * Template file content can be transformed to a String, as is, or
 * template contents can be transformed via variable resolution.
 */
public class Template {

    private static final String START_VARIABLE_DELIMITER = "{{";
    private static final String END_VARIABLE_DELIMITER = "}}";
    private static final Pattern VARIABLE_DELIMITER_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final Path templatePath;
    private final Map<String, String> templateValuesByName;
    private final String rawContent;
    // A fatal exception will be thrown if failOnMissingTemplateValue = true and a template
    // value is not provided, else an empty string "" will be substituted for missing values.
    private final boolean failOnMissingTemplateValue;

    /**
     * Constructor for templates with no variable tokens.
     */
    public Template(String templateName) {
        this(templateName, new HashMap<>(), false);
    }

    public Template(String templateName,
                    Map<String, String> templateValuesByName,
                    boolean failOnMissingTemplateValue) {
        this.templatePath = getPath(templateName);
        this.templateValuesByName = templateValuesByName;
        this.rawContent = toText.apply(templatePath);
        this.failOnMissingTemplateValue = failOnMissingTemplateValue;
        validateNumExpectedVariables();
        validateVariableValues();
    }

    public Path getTemplatePath() {
        return this.templatePath;
    }

    /**
     * Returns template content as a String if there are no template variables to
     * resolve, else returns content transformed by variable substitution.
     *
     * @return String
     */
    public String getContent() {
        return templateValuesByName.isEmpty() ? rawContent : getResolvedContent();
    }

    /**
     * Return content transformed by variable substitution.
     */
    private String getResolvedContent() {
        validateTemplateVariableNames();
        return doVariableSubstitution();
    }

    /**
     * Returns this template's resolved content with the given code examples text
     * inserted at the second line of the original content.  The code examples
     * argument is inserted as is;  no variable substitution is attempted on
     * code snippets.
     */
    public String getContentWithCodeExamples(String codeExamples) {
        if (codeExamples == null || codeExamples.isBlank())
            throw new IllegalArgumentException("Code examples text is null or empty.");

        // Any necessary variable substitution happens before insertion.
        String originalContent = getContent();
        StringBuilder contentBuilder = new StringBuilder(originalContent);
        int codeInsertionIdx = contentBuilder.indexOf("\n") + 1;
        contentBuilder.insert(codeInsertionIdx, codeExamples);
        contentBuilder.append("\n");
        return contentBuilder.toString();
    }

    /**
     * Return transformed template content.  Provided values are substituted for
     * template variable tokens, and template variable delimiters are removed.
     */
    private String doVariableSubstitution() {
        String variableNamesRegex = String.join("|", templateValuesByName.keySet());
        Pattern pattern = Pattern.compile(variableNamesRegex);
        Matcher matcher = pattern.matcher(rawContent);
        StringBuilder resolvedContentBuilder = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(resolvedContentBuilder, templateValuesByName.get(matcher.group()));

        matcher.appendTail(resolvedContentBuilder); // copy remainder of input sequence
        return removedVariableDelimiters(resolvedContentBuilder);
    }

    /**
     * Returns transformed template content stripped of all '{{' and '}}' template variable delimiters.
     */
    private String removedVariableDelimiters(StringBuilder resolvedContentBuilder) {
        String halfClean = resolvedContentBuilder.toString().replaceAll("\\{\\{", "");
        String allClean = halfClean.replaceAll("}}", "");
        return allClean.trim() + "\n\n";
    }

    /**
     * Throws an exception if the variable names passed into the Template constructor
     * do not exactly match the variable names found in the template (.md) file.
     */
    private void validateTemplateVariableNames() {
        // The expected variables names are those provided in the Template constructor.
        List<String> expectedVariableNames = templateValuesByName.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
        List<String> templateVariableNames = findVariableNamesInTemplate();
        boolean isMatch = expectedVariableNames.equals(templateVariableNames);
        if (!isMatch)
            throw new IllegalStateException(format("Variable names passed into the Template"
                            + " constructor do not match variable names found in template file %s.%n"
                            + "Compare your template file content with the templateValuesByName argument.",
                    templatePath.getFileName().toString()));
    }

    /**
     * Return list of all variable names found in the template (.md) file.
     */
    private List<String> findVariableNamesInTemplate() {
        List<String> variables = new ArrayList<>();
        Matcher m = VARIABLE_DELIMITER_PATTERN.matcher(rawContent);
        while (m.find())
            variables.add(m.group(1).trim());

        Collections.sort(variables);
        return variables;
    }

    /**
     * Throws an exception if the number of variables passed into the Template constructor
     * do not exactly match the number of variables tokens found in the template (.md) file.
     */
    private void validateNumExpectedVariables() {
        int numStartVarTokens = getVariableDelimiterCount(START_VARIABLE_DELIMITER);
        int numEndVarTokens = getVariableDelimiterCount(END_VARIABLE_DELIMITER);
        if ((numStartVarTokens != templateValuesByName.size()) || (numEndVarTokens != templateValuesByName.size()))
            throw new IllegalArgumentException(
                    format("Incorrect templateValuesByName argument passed to Template constructor,"
                                    + " or template %s content is malformed.%n"
                                    + "# of Template constructor templateValuesByName elements = %d%n"
                                    + "# of template file's start variable delimiters = %d%n"
                                    + "# of template file's end variable delimiters = %d%n",
                            templatePath.getFileName().toString(),
                            templateValuesByName.size(),
                            numStartVarTokens,
                            numEndVarTokens));
    }

    /**
     * If class level field failOnMissingTemplateValue == true, an exception is thrown if any variable value passed
     * into the Template constructor is null or blank.
     * <p>
     * Else, if class level field failOnMissingTemplateValue == false, an empty string will be substituted for any
     * null or blank variable passed into the Template constructor.
     */
    private void validateVariableValues() {
        for (Map.Entry<String, String> varEntry : templateValuesByName.entrySet()) {
            String name = varEntry.getKey();
            String value = varEntry.getValue();
            if (value == null || value.isBlank()) {
                if (failOnMissingTemplateValue) {
                    String errMsg = format("Required %s template variable '%s' not specified.\n\tTemplate Vars:  %s",
                            templatePath.getFileName().toString(),
                            name,
                            templateValuesByName);
                    throw new IllegalArgumentException(errMsg);
                } else {
                    // Do not fail;  substitute the empty string for the missing template value.
                    varEntry.setValue("");
                }
            }
        }
    }

    /**
     * Return the number of occurrences a variable token delimiter
     * ('{{' or '}}') is found in the template (.md) file's raw content.
     */
    private int getVariableDelimiterCount(String delimiter) {
        int count = 0, index = 0;
        while ((index = rawContent.indexOf(delimiter, index)) != -1) {
            count++;
            index++;
        }
        return count;
    }

    /**
     * Returns a Path object for the given template file name.
     */
    private Path getPath(String templateName) {
        File file = getFileFromResource(templateName);
        return file.toPath();
    }

    /**
     * Returns a File object for the given template file name.
     */
    private File getFileFromResource(String templateName) {
        URL resource = getClass().getClassLoader().getResource("templates" + File.separatorChar + templateName);
        if (resource == null) {
            throw new IllegalArgumentException("Resource template " + templateName + " not found.");
        }
        try {
            return new File(resource.toURI());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Resource template " + templateName + " has bad URI syntax.", ex);
        }
    }
}
