A better class lookup engine for YU courses
## Instructions
1. Open the Regular class lookup, select your term
2. Open your browser's inspect tool and navigate to the "Network" tab
3. Now hit search without specifying any parameters. This ensures that you pull the whole dataset.
4. Right click on the GET request that has the type "json" and select "Copy as cURL" under the copy option.
5. Paste this into the terminal, and then change the "pageMaxSize" to 1000. Pipe the output into a file called classes1.json
6. Run the command again, but with the field "pageOffset" set to 500. Pipe the output into a file called classes2.json.
7. Paste the following into the terminal
```
cat classes*.json | jq -r '.data[] | [
  .courseReferenceNumber, 
  .courseNumber, 
  .subject, 
  .sequenceNumber, 
  .campusDescription, 
  ((.courseTitle) | gsub(","; ";")), 
  (.creditHours//.creditHourLow), ((.faculty[0].displayName // "Staff") | gsub(","; ";")), 
  .enrollment, 
  .maximumEnrollment, 
  .seatsAvailable, 
  .waitCount, 
  .waitCapacity, 
  .waitAvailable, 
  (.meetingsFaculty | map(
      ([
        (if .meetingTime.monday then "M" else empty end),
        (if .meetingTime.tuesday then "T" else empty end),
        (if .meetingTime.wednesday then "W" else empty end),
        (if .meetingTime.thursday then "R" else empty end),
        (if .meetingTime.friday then "F" else empty end),
        (if .meetingTime.sunday then "S" else empty end)
      ] | join("||")) + "}" + 
      (.meetingTime.beginTime // "N/A") + "}" + 
      (.meetingTime.endTime // "N/A") + "}" +
      (.meetingTime.building // "TBA") + "}" + (.meetingTime.room // "")
    ) | join("::")),
  (.sectionAttributes | map(.code) | join("||")), 
  (.sectionAttributes | map(.description) | join("||"))
] | @csv'
```
8. Pipe this into a file with a name of your choice.
9. Run Search with the file path as the following argument. Make sure the path is from root, and any escaped spaces should be changed to be not escaped.
10. Use the TUI, it has instructions
