# ParamScope
ParamScope: A Hybrid Analysis Tool for Detecting Cryptographic Misuse in Java Applications.

- 🔍 **Precise Parameter Reconstruction**: Combines static slicing with dynamic IR simulation.
- 🛡️ **Security-Centric Analysis**: Detects non-standard cryptographic implementations (e.g., encoded parameter values).


### 🛠️ Building From Source

Ensure following dependencies are installed:
- Java 17
- Maven 3.9.6

The executable JAR file `'ParamScope-x.x-SNAPSHOT-jar-with-dependencies.jar'` has been placed in the project directory.

If you want to build from source, run following commands:
```bash
git clone ...
cd paramscope
mvn package clean
```

The executable jar file `'ParamScope-x.x-SNAPSHOT-jar-with-dependencies.jar'` will be generated in 'target/' directory.

### 📁 Project Structure
- **`ParamScope\`**: ParamScope source code.
- **`Evaluation_Benchmarks\`**: RQ1 & RQ2: evaluation results of ParamScope on 5 benchmarks.
- **`Evaluation_Apps\`**: RQ3 & RQ5: evaluation results of ParamScope, Cryptoguard, CogniCryptSAST on 327 popular Google Play apps.
- **`Case_Verification\`**: RQ4: representative cases from real-world apps, including:
  - **`Array Semantic`**: Generating insecure array values via array manipulation semantics.
  - **`Coded Variable`**: Retrieving insecure values from encoded variables.
  - **`Encapsulation`**: Obtaining insecure parameters from data structure encapsulation(e.g., List-wrapped values).
  - **`Hashing Semantic`**: Deriving insecure constants through hash-based workflows.
  - **`Program Data`**: Extracting insecure values from certain data in the program.
  - **`String Semantic`**: Constructing insecure values via String operations(e.g., StringBuilder).

### 🚀 Run Analysis

#### Analyse jar files
```bash
java -jar ParamScope-x.x-SNAPSHOT-jar-with-dependencies.jar -jar <path_to_jar_file>
```

#### Analyse android apks
```bash
java -jar ParamScope-x.x-SNAPSHOT-jar-with-dependencies.jar -apk <path_to_apk_file> ( -androidJar | -aj ) <path_to_android.jar_file(without stub)>
```

Note: When analyzing Android applications, the -androidJar/-aj option is required. You must specify a non-stub android.jar file that includes concrete implementations. It is recommended to use the `android-all-15-robolectric-12650502.jar` file located in the project directory. The jar can also be downloaded from [Google Android All Library](https://mvnrepository.com/artifact/org.robolectric/android-all).

### 💡 Explanatory Case

ParamScope aims to precisely reconstruct Cryptographic API parameter in Java programs, focusing on:
- Immediate values and direct assignments.
- Values from method parameters.
- Field-based assignments.
- Expression-derived values.
- Reassigned method parameters (for reference types)
- Implicit (static) field assignments in method call

ParamScope performs program slicing and reconstructs parameter values. The case below is a piece of complex, uncommon, and handcrafted code, but clearly demonstrates how ParamScope analyzes value propagation.

**Program Slicing**

![ProgramSlicing](./img/ProgramSlicing-ExplanatoryCase.png)

**Value Reconstruction**

![ValueReconstruction](./img/ValueReconstruction-ExplanatoryCase.png)