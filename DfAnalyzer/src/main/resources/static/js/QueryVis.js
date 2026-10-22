/**
 * @author Débora Pina
 * @email deborabpina@poli.ufrj.br
 */
var projections = {};
var selections = {};
var types = {};
       
var dataset = new Vue({
  el: '#dataset',
  data: {
    dataSetSelectedAtt: {}, 
    CheckedAtt: [],
    QueryType: [],
    Conditions: null,
    types: null,
    s_projections: "",
    s_selections: "",
    s_source: "",
    s_target: "",
    origin: [],
    destination: [],
    rows: null
  },
  computed: {
    QueryType: {
      get: function() {
        return this.qtypes;
      },
      set: function(newValue) {
        this.qtypes = newValue;
      }
    },  
    Conditions: {
      get: function() {
        return this.conditions;
      },
      set: function(newValue) {
        this.conditions = newValue;
      }
    }
  },
  methods: {
      save: function(){     
        var selectedDataset = app.$data.selectedElemet.name;
        var edges = app.$data.graph.edges;
        var nodes = app.$data.graph.nodes;
        var foundObject =  app.$data.graph.nodes.filter(function(e) {
                                return e.label == selectedDataset;
                              });
        var id = foundObject[0]['id'];
        var name = foundObject[0]['label'];
        
        this.dataSetSelectedAtt[selectedDataset] = this.CheckedAtt;
        
        var id_nodes = [];
        for (var i=0; i<nodes.length; i++){
            id_nodes.push(nodes[i]['id']);
        }
        
        conds = this.$data.Conditions || "";
        if (conds !== ""){
            conds = conds.split(" ");
            for (var i=0; i<conds.length; i++){
                if (this.$data.s_selections.indexOf(conds[i]) === -1){
                    this.$data.s_selections += conds[i] + ";";
                }
            }   
        }

        sels = this.dataSetSelectedAtt[selectedDataset] || "";
        if (sels !== ""){
            for (var i=0; i<sels.length; i++){
                if (this.$data.s_projections.indexOf(selectedDataset + "." + sels[i]) === -1){
                    this.$data.s_projections += selectedDataset + "." + sels[i] + ";";
                }
            }
        }  
       
        
        sel_types = this.$data.QueryType;        
        if (sel_types.length === 0) {
            types = "logical";
        }
        else if (sel_types.length === 2){
            types = 'hybrid';
        }
        else {
            types = sel_types[0];
        }        
        
        var selected = [];
        var origin = [];
        var destination = [];
        
        var selected = $('#ds-selected').val();
        if (selected !== '') selected = JSON.parse(selected);
        else selected = [];
        if (!selected.includes(id)){selected.push(id);}
        $('#ds-selected').val(JSON.stringify(selected));
   
        if (selected.length === 1){
//            origin.push(id);
//            destination.push(id);
        }
               
        else {
            var origin = $('#ds-origins').val();
            if (origin !== '') origin = JSON.parse(origin);
            else origin = [];

            var destination = $('#ds-destinations').val();
            if (destination !== '') destination = JSON.parse(destination);
            else destination = [];
            
            for (var i=0; i < selected.length; i++){
                if (selected[i] !== id){
                    if (bfs(edges, id_nodes, id, selected[i])){                   
                        a = origin.indexOf(selected[i]);
                        origin = origin.slice(a+1,a+2);
                        b = destination.indexOf(id);
                        destination = destination.slice(b+1,b+2);                  
                        if(!origin.includes(id)) origin.push(id);
                        if(!destination.includes(selected[i])) destination.push(selected[i]);
                    }                        
                    else if (bfs(edges, id_nodes, selected[i], id)){
                        a = origin.indexOf(id);
                        origin = origin.slice(a+1,a+2);
                        b = destination.indexOf(selected[i]);
                        destination = destination.slice(b+1,b+2);                                          
                        if(!origin.includes(selected[i])) origin.push(selected[i]);
                        if(!destination.includes(id)) destination.push(id);
                    }
                    
                    else { //acho que nesse caso é o includes
//                        if(!origin.includes(id)) origin.push(id);
//                        if(!destination.includes(id)) destination.push(id);
                    }       
                }        
            }                 
        }        
        console.log('origem:' + origin);
        console.log('destino:' + destination);

        for (var k=0;k<origin.length;k++){
            for (var l=0;l<nodes.length;l++){
                if (nodes[l]['id'] === origin[k]){
                    if (this.$data.s_source.indexOf(nodes[l]['label']) === -1){
                        this.$data.s_source += nodes[l]['label'] + ";";
                    }
                }
            }
        }
        
        for (var k=0;k<destination.length;k++){
            for (var l=0;l<nodes.length;l++){
                if (nodes[l]['id'] === destination[k]){
                    if (this.$data.s_target.indexOf(nodes[l]['label']) === -1){
                        this.$data.s_target += nodes[l]['label'] + ";";
                    }
                }
            }
        }        
        $('#saved').css("display","block");        
      },
      run: function(){
        var context = this;
        dataflowTag = $('#dataflowtag').text();
        dataflowId = getUrlParameters("dfId", "", true);

//        for (var key in context.selections) {
//            if (context.selections.hasOwnProperty(key)) {
//                console.log(key + " -> " + context.selections[key]);
//                for (var i in context.selections[key]){
//                    console.log(context.selections[key][i]);
//                }
//            }
//        }
        q = "mapping(" + types + ")" + "\n";
        
        if (this.$data.s_source !== ""){ 
            q += 'source(' + (this.$data.s_source).substr(0,this.$data.s_source.length-1) + ')\n';
        }
        if (this.$data.s_target !== ""){ 
            q += 'target(' + this.$data.s_target.substr(0,this.$data.s_target.length-1) +')\n';
        }        
        
        if (this.$data.s_projections !== ""){ 
            q += 'projection(' + this.$data.s_projections.substr(0,this.$data.s_projections.length-1) + ')\n';
        }
        if (this.$data.s_selections !== ""){ 
            q += 'selection(' + this.$data.s_selections.substr(0,this.$data.s_selections.length-1) +')';
        }
        
        $.ajax({
            url:'/query_interface/' + dataflowTag + '/' + dataflowId,
            data:{message: q},
            type:'POST',
            success:function(result){
                if(result){
                    var npath = result.indexOf("CurrentPath:");
                    var path = result.substr(npath+12,result.length).replace(/\\/g, "/");
                    generateResult(path + "/query_result.csv");
                    console.log("worked");
                }
            },
            error:function(xhr){
                // 400 carries the validation message produced by QueryGuard (U1/U8):
                // show it instead of failing silently in the console.
                var detail = (xhr && xhr.responseText) ? xhr.responseText
                        : 'Nao foi possivel executar a consulta.';
                console.log('query rejected: ' + detail);
                $('#app').html('<div class="alert alert-danger" role="alert">'
                        + '<strong>Consulta rejeitada.</strong><br>'
                        + $('<div>').text(detail).html() + '</div>');
            } 
        });   
      }
  }
});

function generateResult(csv){
    var context = this;
    $.ajax({
        url:'/query_processing/',
        data:{csv: csv}, 
        type:'POST',
        success:function(result){
            if(result){
//                    $('#app').html(result);
                $('#app').html(result);   
                console.log("csv worked");
            }
        },
        error:function(){
            console.log('csv not worked.');
        } 
    });    
}

function getUrlParameters(parameter, staticURL, decode) {
    /*
     Function: getUrlParameters
     Description: Get the value of URL parameters either from
     current URL or static URL
     Author: Tirumal
     URL: www.code-tricks.com
     */
    try {
        var currLocation = (staticURL.length) ? staticURL : window.location.search,
                parArr = currLocation.split("?")[1].split("&"),
                returnBool = true;

        for (var i = 0; i < parArr.length; i++) {
            parr = parArr[i].split("=");
            if (parr[0] == parameter) {
                if (returnBool)
                    returnBool = true;
                return (decode) ? decodeURIComponent(parr[1]) : parr[1];
            } else {
                returnBool = false;
            }
        }

        if (!returnBool)
            return false;
    } catch (err) {
        return null;
    }
}

 function bfs(edges, id_nodes, start, end){
    var queue = [];
    var visited = [];
    var parents = [];
    var current;

    visited[start] = true;
    queue.push(start);    
    console.log(start + ',' + end);
    while(queue.length){
        current = queue.shift();
        if (current === end){
            console.log("true");
            return true;
        }
        for (var i=0; i<Object.keys(edges).length; i++){
            for (var j=0; j<id_nodes.length; j++){
                if (j !== current && edges[i]['from']===current && edges[i]['to']===j && !visited[j]){
                    parents[i] = current;
                    visited[j] = true;
                    queue.push(j);
                }                
            }
        }
    }
    console.log("false");
    return false;
} 