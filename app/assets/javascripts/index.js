(function() {
  var getAxisMax, getAxisMin, getChartArray, getChartOptions, getPricesFromArray, populateStockHistory, updateStockChart;

  $(function() {
    var ws;
    ws = new WebSocket($("body").data("ws-url"));
    ws.onmessage = function(event) {
      var message;
      message = JSON.parse(event.data);
      switch (message.status) {
        case "OK":
          $("#simulation-form").attr("style", "display:none;")
          return;
        case "update":
          return updateGrid(message);
        default:
          return console.log(message);
      }
    };

    $("#stop-simulation").click(function() {
       ws.send(JSON.stringify({stop: true}));
       $("#simulation-form").attr("style", "");
       $("#watorGrid").attr("style", "display:none;");
       $("#planet").remove();
       return;
    });

    return $("#simulation-form").submit(function(event) {
      event.preventDefault();
      var rows = parseInt($("#rows").val());
      var columns = parseInt($("#columns").val());
      ws.send(JSON.stringify({
        rows: rows,
        columns: columns,
        sharkPopulation: parseInt($("#sharkNum").val()),
        fishPopulation: parseInt($("#fishNum").val()),
        chronosFrequency: parseInt($("#chronosFrequency").val())
      }));
      $("#watorGrid").attr("style", "")
      addTable(rows, columns);
      return;
    });

  });

    function addTable(rows, columns) {

    var myTableDiv = document.getElementById("table");

    var table = document.createElement('TABLE');
    table.id = "planet";
    table.border='0';

    var tableBody = document.createElement('TBODY');
    table.appendChild(tableBody);

    for (var i=0; i<rows; i++){
       var tr = document.createElement('TR');
       tableBody.appendChild(tr);

       for (var j=0; j<columns; j++){
           var td = document.createElement('TD')
           td.setAttribute("id", i+"-"+j)
           td.setAttribute("align", "center");
           td.setAttribute("bgcolor", "#0000FF");
           td.width='20';
           td.height='20';
           td.appendChild(document.createTextNode(" "));
           tr.appendChild(td);
       }
    }
    myTableDiv.appendChild(table);

    }

    updateGrid = function(data) {
       var color;
       if (data.animal == "fish") color = "#008000";
       else if (data.animal == "shark") color = "#FF0000";
       else color = "#0000FF"
       var cell = $("#"+data.position.row + "-" + data.position.column);
       cell.attr("bgcolor", color);
       return;
      };

}).call(this);