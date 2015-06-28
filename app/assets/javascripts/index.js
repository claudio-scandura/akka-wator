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
      addTable(rows, columns);
      return;
    });
  });

    function addTable(rows, columns) {

    var myTableDiv = document.getElementById("watorGrid");

    var table = document.createElement('TABLE');
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
           td.width='2';
           td.height='2';
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

  getPricesFromArray = function(data) {
    var v, _i, _len, _results;
    _results = [];
    for (_i = 0, _len = data.length; _i < _len; _i++) {
      v = data[_i];
      _results.push(v[1]);
    }
    return _results;
  };

  getChartArray = function(data) {
    var i, v, _i, _len, _results;
    _results = [];
    for (i = _i = 0, _len = data.length; _i < _len; i = ++_i) {
      v = data[i];
      _results.push([i, v]);
    }
    return _results;
  };

  getChartOptions = function(data) {
    return {
      series: {
        shadowSize: 0
      },
      yaxis: {
        min: getAxisMin(data),
        max: getAxisMax(data)
      },
      xaxis: {
        show: false
      }
    };
  };

  getAxisMin = function(data) {
    return Math.min.apply(Math, data) * 0.9;
  };

  getAxisMax = function(data) {
    return Math.max.apply(Math, data) * 1.1;
  };

  populateStockHistory = function(message) {
    var chart, chartHolder, detailsHolder, flipContainer, flipper, plot;
    chart = $("<div>").addClass("chart").prop("id", message.symbol);
    chartHolder = $("<div>").addClass("chart-holder").append(chart);
    chartHolder.append($("<p>").text("values are simulated"));
    detailsHolder = $("<div>").addClass("details-holder");
    flipper = $("<div>").addClass("flipper").append(chartHolder).append(detailsHolder).attr("data-content", message.symbol);
    flipContainer = $("<div>").addClass("flip-container").append(flipper);
    $("#stocks").prepend(flipContainer);
    return plot = chart.plot([getChartArray(message.history)], getChartOptions(message.history)).data("plot");
  };

  updateStockChart = function(message) {
    var data, plot, yaxes;
    if ($("#" + message.symbol).size() > 0) {
      plot = $("#" + message.symbol).data("plot");
      data = getPricesFromArray(plot.getData()[0].data);
      data.shift();
      data.push(message.price);
      plot.setData([getChartArray(data)]);
      yaxes = plot.getOptions().yaxes[0];
      if ((getAxisMin(data) < yaxes.min) || (getAxisMax(data) > yaxes.max)) {
        yaxes.min = getAxisMin(data);
        yaxes.max = getAxisMax(data);
        plot.setupGrid();
      }
      return plot.draw();
    }
  };

}).call(this);

//# sourceMappingURL=index.js.map
