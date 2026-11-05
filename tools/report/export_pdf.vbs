Option Explicit
Dim app, doc, inputPath, outputPath, tocCount, tocIndex
inputPath = WScript.Arguments(0)
outputPath = WScript.Arguments(1)
Set app = CreateObject("Word.Application")
app.Visible = False
app.DisplayAlerts = 0
Set doc = app.Documents.Open(inputPath, False, False)
tocCount = doc.TablesOfContents.Count
For tocIndex = 1 To tocCount
    doc.TablesOfContents(tocIndex).Update
Next
doc.Repaginate
For tocIndex = 1 To tocCount
    doc.TablesOfContents(tocIndex).UpdatePageNumbers
Next
doc.Save
doc.ExportAsFixedFormat outputPath, 17
doc.Close False
If app.Documents.Count = 0 Then app.Quit
Set doc = Nothing
Set app = Nothing
WScript.Echo "Report DOCX saved and PDF exported."
