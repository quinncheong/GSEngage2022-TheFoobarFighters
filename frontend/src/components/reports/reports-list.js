import {
  Button,
  Card,
  CardContent,
} from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import { AllDataProvider } from "../../data/AllDataProvider";

export const ReportsList = ( props ) => {
  const renderFileDownloadButton = (params) => {
      return (
          <strong>
              <Button
                  variant="contained"
                  color="primary"
                  size="small"
                  onClick={() => {
                    window.location.href = params.row.col3
                  }}
              >
                  Download
              </Button>
          </strong>
      )
  }

  const reports = AllDataProvider('xlsx');
  const rows = reports.map((report, index) => {
    return {
      id : index,
      project: report.projectName,
      col1: report.fileName,
      col2: report.lastModified,
      col3: report.fileURL,
    };
  });

  const columns = [
    { field: "project", headerName: "Project Name", minWidth: 100, flex: 2},
    { field: "col1", headerName: "File Name", minWidth: 300, flex: 2},
    { field: "col2", headerName: "Last Modified", minWidth: 200, flex:2 },
    { field: "col3", headerName: "Download", minWidth: 200, flex: 2, renderCell: renderFileDownloadButton }
  ];
  
  const handleStateChange = (gridState, e, details) => {
    console.log(gridState);
    console.log(e);
    console.log(details);
  };

  return (
    <form {...props}>
      <Card sx={{ width: "100%" }}>
        {/* <CardHeader
          sx={{ p: 3 }}
          title={`Past Reports`}
        />
        <Divider /> */}
        <CardContent sx={{ height: 800, width: "100%" }}>
          <DataGrid
            sx={{ outline: "none" }}
            hideFooter
            labelRowsPerPage=""
            rowsPerPageOptions={[]}
            checkboxSelection
            rows={rows}
            columns={columns}
            onStateChange={handleStateChange}
          />
        </CardContent>
      </Card>
    </form>
  );
};