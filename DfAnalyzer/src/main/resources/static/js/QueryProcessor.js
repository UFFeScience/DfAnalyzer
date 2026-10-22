/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


var app = new Vue({
    el: "#app",
    data: {
        input_form: {
            source_dataset: ""

        }, table: null
    },
    methods: {
        send: function () {
            var self = this;
            $.ajax({
                type: "POST",
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                url: "/dfview/query",
                data: JSON.stringify(this.input_form), // Note it is important
                success: function (result) {
                    self.table = result;
                },
                error: function (e) {
                    console.log(e);
                    try {
                        showErrorBox(e.responseJSON["error"]);
                    } catch (e) {

                    }
                },
                complete: function () {

                }
            });
        }
    }});