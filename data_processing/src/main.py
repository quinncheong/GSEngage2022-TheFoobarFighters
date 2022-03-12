from dateutil.parser import parse
from traceback import print_tb
from fastapi import Request, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel
import uvicorn
import pandas as pd
import traceback
from io import BytesIO
from fastapi.responses import StreamingResponse

# Helper functions
def get_all_rows(dict_data, headers):
    for key in dict_data:
        if key == 'columns':
            continue
        if key not in headers and not isinstance(dict_data[key], (set, tuple, dict, list)):
            datatype = type(dict_data[key])
            # Check if non-string datatype
            if datatype != str:
                headers[key] = {"datatype": datatype.__name__, "row_count": 1}
            else:
            # Check if is datetime
                try:
                    parse(dict_data[key])
                    headers[key] = {"datatype": "datetime", "row_count": 1}
            # If error, attach other datatype
                except ValueError:
                    headers[key] = {"datatype": datatype.__name__, "row_count": 1}
        elif key not in headers and isinstance(dict_data[key], (set, tuple, dict, list)):
            headers[key] = {}
        else:
            curr_datatype = type(dict_data[key]).__name__
            if headers[key]["datatype"] == "NoneType":
                headers[key]["datatype"] = curr_datatype
            headers[key]["row_count"] += 1 
        if isinstance(dict_data[key], dict):
            get_all_rows(dict_data[key], headers[key])
        elif isinstance(dict_data[key], list):
            [get_all_rows(x, headers[key]) for x in dict_data[key] if isinstance(x, dict)]      
    return


# Run app
PREFIX='/api/v1'
app = FastAPI()

origins = [
    "http://localhost:3000",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Placeholder homepage
@app.get(f"{PREFIX}/")
async def homepage():
    return {"msg": "Welcome to data processing"}


# Process datatype and size endpoint
@app.post(f"{PREFIX}/process")
async def get_body(request: Request):
    input_body =  await request.json()
    
    data_schema = {}
    
    try:
        for report in input_body:
            headers = {}
            get_all_rows(input_body[report], headers)
            data_schema[report] = headers
    except Exception as e:
        traceback.print_exception(type(e), e, e.__traceback__)
        return {"error_msg": repr(e)}

    return data_schema


# Create excel report endpoint
# TODO: Make cols and cols_rename to be dynamic
@app.post(f"{PREFIX}/report", response_description='xlsx')
async def create_table(request: Request):
    df_list = []
    sheet_names = []
    sheet_dict = {}
    
    try:
        input = await request.json()
    except Exception as e:
        return {"error_msg": repr(e)}
    
    try:
        compiled_data = input['compiled']
        data = input['data']
        for k,v in data.items():
            df = pd.DataFrame(v)
            data[k] = df
        # build each dataframe/table
        for k,v in compiled_data.items():
            for table_row in v:
                row_list = []
                for table in table_row:
                    series_list = []
                    for col, json_dict in table.items():
                        if json_dict['sum']:
                            name = tmp_series.name
                            s = pd.Series([tmp_series.agg('sum')])
                            tmp_series = tmp_series.append(s, ignore_index=True)
                            tmp_series.name = name
                            
                        if json_dict['data'] in data and col in data[json_dict['data']]:
                            tmp_series = data[json_dict['data']][col]
                            if col == 'maturityDate':
                                tmp_series = pd.to_datetime(tmp_series, format='%d/%m/%Y').dt.date
                                tmp_series.name = 'Maturity'
                            elif col == 'positionDate':
                                tmp_series = pd.to_datetime(tmp_series, format='%Y%m%d').dt.date
                                tmp_series.name = 'Position Date'
                            elif col == 'compositionRate':
                                tmp_series = ( 100 * tmp_series ).round(2).astype(str) + "%"
                                tmp_series.name = 'Allocation'
                            elif col.islower() and col.isalpha():
                                tmp_series.name = col.capitalize()
                            elif not col.isupper():
                                substr = ''
                                lst = []
                                for ch in col:
                                    if ch.isupper():
                                        if substr != '':
                                            lst.append(substr.capitalize())
                                            substr = ''
                                    if ch != '_':
                                        substr += ch
                                if substr != '':
                                    lst.append(substr.capitalize())
                                tmp_series.name = ' '.join(lst)
                                
                        series_list.append(tmp_series)
                        tmp_series = pd.Series()
                    # build dataframe
                    if series_list != []:
                        tmp_df = pd.concat(series_list, axis=1)
                        tmp_df.fillna('', inplace=True)
                        row_list.append(tmp_df)
                if row_list != []:
                    df_list.append(row_list)
            if df_list != []:
                sheet_dict[k] = df_list
                df_list = []

        # write excel
        output_excel = BytesIO()
        writer = pd.ExcelWriter(output_excel,
                                engine='xlsxwriter',
                                date_format='yyyymmdd')
        workbook = writer.book
        for sheet_name, tables in sheet_dict.items():
            start_col = 0
            start_row = 0
            for table_rows in tables:
                max_row = 0
                for table_df in table_rows:
                    table_df.to_excel(writer, sheet_name=sheet_name, startrow=start_row, startcol=start_col, index=False)
                    (r, c) = table_df.shape
                    if r > max_row:
                        max_row = r
                    worksheet = writer.sheets[sheet_name]
                    for column in table_df:
                        column_width = max(table_df[column].astype(str).map(len).max(), len(column))
                        col_idx = table_df.columns.get_loc(column)
                        col_idx += start_col
                        if col_idx in column_width_dict:
                            if column_width_dict[col_idx] < column_width:
                                column_width_dict[col_idx] = column_width
                                writer.sheets[sheet_name].set_column(col_idx, col_idx, column_width)
                        else:
                            column_width_dict[col_idx] = column_width
                            writer.sheets[sheet_name].set_column(col_idx, col_idx, column_width)
                    start_col += c + 1
                start_col = 0
                start_row += max_row + 3
            column_width_dict = {}
        writer.save() 
        xlsx_data = output_excel.getvalue()
        file_name = 'output'

        return StreamingResponse(
            BytesIO(xlsx_data), 
            media_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 
            headers={
                "Content-Disposition": f'attachment; filename="{file_name}.xlsx"'
        })

    except Exception as e:
        return {"error_msg": repr(e)}


if __name__ == "__main__":
    uvicorn.run("main:app", port=8000, reload=True)