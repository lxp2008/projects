import os
import shutil

# --- Configuration ---
# The new project will be created in this directory
output_dir = "springmvc"
# The root directory of the Struts project to be converted
project_root = "."

def get_file_type(file_path, content):
    """
    Determines the type of a file to generate a specific conversion prompt.
    This function analyzes the file's path and content to categorize it.
    """
    if "migration_script.py" in os.path.basename(file_path):
        return "skip"
    if file_path.endswith("pom.xml"):
        return "pom.xml"
    if file_path.endswith("web.xml"):
        return "web.xml"
    if "struts-config.xml" in os.path.basename(file_path):
        return "struts-config"
    if file_path.endswith("validation.xml"):
        return "struts-validation"
    if file_path.endswith(".java"):
        if "extends ActionForm" in content:
            return "struts-form"
        if "extends Action" in content:
            return "struts-action"
        if "import org.apache.struts." in content:
            return "struts-java-other"
    if file_path.endswith(".jsp"):
        if 'uri="http://struts.apache.org/tags-bean"' in content or \
           'uri="http://struts.apache.org/tags-html"' in content or \
           'uri="http://struts.apache.org/tags-logic"' in content:
            return "struts-jsp"

    # If the file does not match any Struts-related criteria, it will be copied directly.
    return "other"

def generate_prompt(file_type, content, file_path):
    """
    Generates a tailored, detailed prompt for the conversion.
    """
    base_prompt = (
        "You are an expert Java developer specializing in framework migration. "
        "Your task is to convert a file from an old Struts 1 project to its modern equivalent in a Spring MVC + JSP project. "
        "Preserve original business logic, comments, and structure as much as possible in the new format. "
        "The output should be ONLY the complete code for the new file, without any explanations, introductions, or markdown formatting like ```java."
    )

    prompts = {
        "pom.xml": (
            f"{base_prompt}\n\nThis file is the `pom.xml` for the project. Please update it to: "
            "1. Remove Struts 1 dependencies. 2. Add dependencies for Spring MVC 5.x. "
            "3. Add JSTL dependencies. 4. Upgrade the Java version to 1.8. "
            "5. Replace `hdiv-struts-1` with `hdiv-spring-mvc`.\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        ),
        "web.xml": (
            f"{base_prompt}\n\nThis is the `web.xml`. Convert it to use Spring's `DispatcherServlet` instead of Struts' `ActionServlet`. "
            "Map the `DispatcherServlet` to the root ('/'). Point it to a Java-based configuration class (e.g., `...WebAppConfig`).\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        ),
        "struts-config": (
            f"{base_prompt}\n\nThis is a Struts 1 configuration file (`struts-config.xml`). Convert its contents into a Spring MVC Java-based configuration class. "
            "The class should have `@Configuration`, `@EnableWebMvc`, and `@ComponentScan`. It must also define a `ViewResolver` bean for JSPs.\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        ),
        "struts-action": (
            f"{base_prompt}\n\nThis is a Struts 1 `Action` class. Convert it to a Spring MVC `@Controller`. "
            "Replace `ActionForward` with a `String` return type for the view name. Replace `ActionForm` with a `@ModelAttribute` annotated POJO. "
            "Use `@GetMapping` or `@PostMapping` for request mappings.\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        ),
        "struts-form": (
            f"{base_prompt}\n\nThis is a Struts `ActionForm` bean. Convert it into a simple POJO. "
            "Remove `extends ActionForm` and any Struts-specific methods like `reset()` or `validate()`.\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        ),
        "struts-java-other": (
            f"{base_prompt}\n\nThis is a Java class with Struts imports. Refactor it to remove Struts dependencies, replacing them with Spring or standard Java EE equivalents.\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        ),
        "struts-jsp": (
            f"{base_prompt}\n\nThis is a JSP using Struts tags (`html:`, `bean:`, `logic:`). Convert it to use JSTL core tags (`c:`) and Spring Form tags (`form:`). "
            "Replace `<html:form>` with `<form:form>`, `<bean:write>` with `${...}`, and `<logic:iterate>` with `<c:forEach>`.\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        ),
        "struts-validation": (
            f"{base_prompt}\n\nThis is a Struts validation file (`validation.xml`). Convert its rules into Java Bean Validation annotations (e.g., `@NotNull`, `@Size`) on the corresponding model POJOs.\n\n"
            f"File path: `{file_path}`\n\n--- Start of File Content ---\n{content}\n--- End of File Content ---"
        )
    }
    return prompts.get(file_type)

def process_project(root_dir, output_dir):
    """
    Walks through the project, copies non-struts files, and generates conversion prompts.
    """
    print(f"--- Starting project processing ---")
    print(f"--- Output directory will be: {os.path.abspath(output_dir)} ---")

    prompts_to_execute = []

    if os.path.exists(output_dir):
        print(f"Output directory '{output_dir}' already exists. Removing it for a clean start.")
        shutil.rmtree(output_dir)
        
    os.makedirs(output_dir)

    for dirpath, _, filenames in os.walk(root_dir):
        # Skip the output and VCS directories
        if os.path.abspath(dirpath).startswith(os.path.abspath(output_dir)) or \
           ".git" in dirpath or ".svn" in dirpath:
            continue

        relative_dir = os.path.relpath(dirpath, root_dir)
        new_dir = os.path.join(output_dir, relative_dir)
        if not os.path.exists(new_dir):
            os.makedirs(new_dir)

        for filename in filenames:
            if filename == "migration_script.py":
                continue

            file_path = os.path.join(dirpath, filename)
            new_file_path_final = os.path.join(new_dir, filename)

            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                is_text = True
            except:
                is_text = False

            if not is_text:
                shutil.copy(file_path, new_file_path_final)
                continue

            file_type = get_file_type(file_path, content)

            if file_type == "other" or file_type == "skip":
                shutil.copy(file_path, new_file_path_final)
                continue

            # --- Handle renaming for specific file types ---
            final_filename = filename
            if file_type == "struts-action":
                final_filename = filename.replace("Action.java", "Controller.java")
            elif file_type == "struts-config":
                parent_dir_name = os.path.basename(os.path.dirname(file_path)).title()
                final_filename = f"{parent_dir_name}WebAppConfig.java"

            new_file_path_final = os.path.join(new_dir, final_filename)
            # --- End of renaming ---

            prompt = generate_prompt(file_type, content, file_path)
            if prompt:
                prompts_to_execute.append({
                    "target_file": new_file_path_final.replace("\\", "/"), # Use forward slashes for consistency
                    "prompt": prompt
                })

    # --- Print all collected prompts at the end ---
    print("\n\n" + "="*80)
    print("--- CONVERSION PROMPTS GENERATED ---")
    print(f"--- Found {len(prompts_to_execute)} Struts files to convert. ---")
    print("--- Non-Struts files have been copied to the '{}' directory. ---".format(output_dir))
    print("\nINSTRUCTIONS:")
    print("1. For each prompt below, copy the entire text from '--- PROMPT START ---' to '--- PROMPT END ---'.")
    print("2. Paste it into the chat.")
    print("3. The AI will provide the converted code, which will be saved to the specified 'TARGET FILE'.")
    print("="*80 + "\n")

    for i, item in enumerate(prompts_to_execute):
        print(f"--------------- PROMPT {i+1} of {len(prompts_to_execute)} ---------------")
        print(f"TARGET FILE: {item['target_file']}")
        print("\n--- PROMPT START ---\n")
        print(item['prompt'])
        print("\n--- PROMPT END ---\n")
        print("-" * 50 + "\n\n")

    print("--- All prompts generated. ---")

def main():
    """Main function to run the script."""
    process_project(project_root, output_dir)

if __name__ == "__main__":
    main() 