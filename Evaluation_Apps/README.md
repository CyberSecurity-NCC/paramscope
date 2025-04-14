### RQ3

We collected runtime logs to record the execution time of ParamScope at each analysis stage. Since the full log files are too large, we retained only the timing information in the `ParamScope_timeLog.zip` archive.

Note: when collecting the runtime statistics, we used a script to filter out apps that produced no analysis results.

|                       | min (s)    | max (s) | average (s)     |
|-----------------------|------------|---------|-----------------|
| Pre-processing        | 1          | 2,582   | 54.84           |
| Call-graph build      | 10         | 1,404   | 343.61          |
| Value resolving       | 1          | 135     | 39.97           |
| Total time            | 13         | 2,792   | 438.42          |

 Class numbers per app:  123 ~ 95,386 (Average: 23,264)                          

### RQ5

**Directory structure**

- `cryptoguard-04.05.03_original.jar`: original Cryptoguard executable jar.
- `cryptoguard-04.05.03_countOnce.jar`: We observed that Cryptoguard generates reports based on the number of bytes when checking predicatble arrays. For example, Cryptoguard produces four reports for the constant key [0, 0, 0, 0]. Therefore, we made a brief modification to the original Cryptoguard so that for each predictable array, only one report is generated per call site.
- `ParamScope_results.zip`: ParamScope analysis results on 327 apps, Due to the large size of visualization result pngs, only the json report files are retained.
- `CogniCryptSAST_results.zip`: CogniCryptSAST analysis results on 327 apps, csv report format.
- `Cryptoguard_results.zip`: Cryptoguard analysis results on 327 apps, json report format.
- `Cryptoguard_results_countOnce.zip`: Cryptoguard (cryptoguard-04.05.03_countOnce.jar) analysis results on 327 apps, json report format.

**Comparison between ParamScope and CogniCryptSAST**

ParamScope and CogniCryptSAST obtained analyzable results in 69 overlapping apps. In these apps, CogniCryptSAST reported 3207 results, whereas ParamScope reported 4075 results.

When calculating the CogniCryptSAST (CrySL) results, we excluded misuse reports related to SSL and only counted results pertaining to the misuse of cryptographic API parameter values, with the logic below:
```python
with open(result_csv_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()
    # Process only if there is at least one data line beyond headers
    if len(lines) > 1:
        for entry in lines[1:]:
            columns = entry.strip().split(';')
            if ("javax.net.ssl" in columns[2] or (not "ConstraintError" in columns[1])):
                continue  # Do not count this result
            total_results += 1
```

Normally count ParamScope's results with the logic below: 

```python
with open(paramscope_result_json, 'r', encoding='utf-8') as jf:
    data = json.load(jf)
    results = data.get("results", [])
    for res in results:
        param_instances = res.get("paramInstances", [])
        for instance in param_instances:
            security_info = instance.get("securityInfo", "")
            # Check if securityInfo is exactly "(Repeated Generation Test: Constant)" 
            # or contains "(Not in whitelist value:"
            if security_info == "(Repeated Generation Test: Constant)" or "(Not in whitelist value:" in security_info:
                total_results += 1
```

**Comparison between ParamScope and Cryptoguard**

ParamScope and Cryptoguard obtained analyzable results in {TODO} overlapping apps. In these apps, Cryptoguard reported {TODO} results, whereas ParamScope reported {TODO} results.

When calculating the Cryptoguard (cryptoguard-04.05.03_countOnce.jar) results, SSL and insecure PRNG related reports are excluded, with the logic below:

```python
# Excluded results
excluded = {
    "Used untrusted PRNG"，
    "Should check HostnameVerification manually",
    "Uses untrusted TrustManager",
    "Used HTTP Protocol",
    "Used untrusted HostNameVerifier"
}

with open(cryptoguard_result_json, 'r', encoding='utf-8') as f:
    data = json.load(f)
    issues = data.get("Issues", [])
    # Exclude SSL related cases
    count_for_file = sum(
        1
        for issue in issues
        if issue.get("RuleDesc", "") not in excluded
    )
    total_results += count_for_file
```

Therefore, when counting ParamScope's results, each call site for array values should be counted only once, with the logic below:

```python
with open(paramscope_result_json, 'r', encoding='utf-8') as jf:
    data = json.load(jf)
    results = data.get("results", [])
    # For each result in the JSON file, process array instances (for each callsite, array instances only count once.)
    for res in results:
        param_instances = res.get("paramInstances", [])
        repeated_found = False
        not_in_whitelist_count = 0
        for instance in param_instances:
            security_info = instance.get("securityInfo", "")
            if security_info == "(Repeated Generation Test: Constant)":
                repeated_found = True
            elif "(Not in whitelist value:" in security_info:
                not_in_whitelist_count += 1
        # For the entire paramInstances, count only one for array Instances (if found),
        # and count each instance with "(Not in whitelist value:" normally.
        total_json_entries += (1 if repeated_found else 0) + not_in_whitelist_count
```