<%@ page import="com.macrosolution.mpm.store.StoreLimit; com.macrosolution.mpm.claim.StoreLimits; com.macrosolution.mpm.shipper.fedex.FedexProductType; com.macrosolution.mpm.shipper.ShipperType;" contentType="text/html;charset=UTF-8" %>
<g:set var="maxConfs" value="${2}"/>
<g:set var="indexToOpen" value="${Long.parseLong(params.confOffset ?: "0")}"/>
<g:set var="multiLimit" value="${poleepo.getLimit([limit: StoreLimits.MULTI_SHIPPER])}"/>

<g:set var="shipper" value="${shippers && shippers[0] ? shippers[0] : null}" />

<script type="text/javascript">

    let fedex_nationalProductTypeCounter = [];
    let fedex_internationalProductTypeCounter = [];

    <g:each var="k" in="${0..maxConfs}">
    <g:set var="s" value="${shippers && shippers[k] ? shippers[k] : null}" />
    fedex_nationalProductTypeCounter.push(${s && s.nationalProductTypeStored ? s.nationalProductTypeStored?.size() : 1})
    fedex_internationalProductTypeCounter.push(${s && s.internationalProductTypeStored ? s.internationalProductTypeStored?.size() : 1})
    </g:each>

    $(document).ready(function(){
        setUpPage();

        <g:each var="j" in="${0..maxConfs}">
        <g:set var="shipper" value="${shippers && shippers[j] ? shippers[j] : null}" />
        if (${nationalProductTypes?.size() == (s && s.nationalProductTypeStored? s.nationalProductTypeStored.size() : 1)}) {
            $("#fedexAddNationalProductServiceType${j}").hide()
        };

        if (${internationalProductTypes?.size() == (s && s.internationalProductTypeStored? s.internationalProductTypeStored.size() : 1)}) {
            $("#fedexAddInternationalProductServiceType${j}").hide()
        };
        </g:each>

    })

    function fedex_addNationalProductType(supplierIndex) {
        var nationalProductTypeSize = ${nationalProductTypes?.size()}

        if (nationalProductTypeSize > fedex_nationalProductTypeCounter[supplierIndex]) {
            $("#nationalNumberProductType" + supplierIndex).val(parseInt($("#nationalNumberProductType" + supplierIndex).val()) + 1)

            fedex_nationalProductTypeCounter[supplierIndex]++;

            $("<div/>", {id: "nationalProductTypeValues" + fedex_nationalProductTypeCounter[supplierIndex] + "" + supplierIndex, class: "row m-t-sm"})
                .appendTo("#nationalProductTypeForm" + supplierIndex);

            $("<input/>", {type: "hidden", name: "index", value: fedex_nationalProductTypeCounter[supplierIndex]})
                .appendTo("#nationalProductTypeValues" + fedex_nationalProductTypeCounter[supplierIndex] + "" + supplierIndex);

            $("<div/>", {class: "col-sm-10"})
                .append($("<div/>", {class: "input-group"})
                    .append($("<select/>", {class: "form-control", name: "nationalProductType[]" + supplierIndex})
                        <g:each var="opt" in="${nationalProductTypes}">
                            .append($("<option/>", {value: "${opt.code}", text: "${opt.description}"}))
                        </g:each>)
                ).appendTo("#nationalProductTypeValues" + fedex_nationalProductTypeCounter[supplierIndex] + "" + supplierIndex);

            $("<div/>", {class: "col-sm-2"})
                .append($("<a/>", {onclick: "fedex_deleteNationalProductType(" + fedex_nationalProductTypeCounter[supplierIndex] + "," + supplierIndex + ")"})
                    .append($("<i/>", {class: "fa fa-trash fa-2x"})))
                .appendTo("#nationalProductTypeValues" + fedex_nationalProductTypeCounter[supplierIndex] + "" + supplierIndex);
        }

        if (nationalProductTypeSize === fedex_nationalProductTypeCounter[supplierIndex]) {
            $("#fedexAddNationalProductServiceType" + supplierIndex).hide()
        }
    }

    function fedex_addInternationalProductType(supplierIndex) {
        var internationalProductTypeSize = ${internationalProductTypes?.size()};

        if(internationalProductTypeSize > fedex_internationalProductTypeCounter[supplierIndex]) {
            $("#internationalNumberProductType" + supplierIndex).val(parseInt($("#internationalNumberProductType" + supplierIndex).val()) + 1)

            fedex_internationalProductTypeCounter[supplierIndex]++;

            $("<div/>", {id: "internationalProductTypeValues" + fedex_internationalProductTypeCounter[supplierIndex] + "" + supplierIndex, class: "row m-t-sm"})
                .appendTo("#internationalProductTypeForm" + supplierIndex);

            $("<input/>", {type: "hidden", name: "index", value: fedex_internationalProductTypeCounter[supplierIndex]})
                .appendTo("#internationalProductTypeValues" + fedex_internationalProductTypeCounter[supplierIndex] + "" + supplierIndex);

            $("<div/>", {class: "col-sm-10"})
                .append($("<div/>", {class: "input-group"})
                    .append($("<select/>", {class: "form-control", name: "internationalProductType[]" + supplierIndex})
                        <g:each var="opt" in="${internationalProductTypes}">
                            .append($("<option/>", {value: "${opt.code}", text: "${opt.description}"}))
                        </g:each>))
                .appendTo("#internationalProductTypeValues" + fedex_internationalProductTypeCounter[supplierIndex] + "" + supplierIndex);

            $("<div/>", {class: "col-sm-2"})
                .append($("<a/>", {onclick: "fedex_deleteInternationalProductType(" + fedex_internationalProductTypeCounter[supplierIndex] + "," + supplierIndex + ")"})
                    .append($("<i/>", {class: "fa fa-trash fa-2x"}))
                ).appendTo("#internationalProductTypeValues" + fedex_internationalProductTypeCounter[supplierIndex] + "" + supplierIndex);
        }

        if (internationalProductTypeSize === fedex_internationalProductTypeCounter[supplierIndex]) {
            $("#fedexAddInternationalProductServiceType" + supplierIndex).hide()
        }
    }

    function fedex_deleteNationalProductType(index, supplierIndex) {
        var value = parseInt($("#nationalNumberProductType" + supplierIndex).val());
        // if(value > 1) {
        $("#nationalProductTypeValues" + index + "" +  supplierIndex).remove();
        $("#nationalNumberProductType" + supplierIndex).val(value - 1);
        fedex_nationalProductTypeCounter[supplierIndex] -= 1
        $("#fedexAddNationalProductServiceType" + supplierIndex).show()
        // }
    }

    function fedex_deleteInternationalProductType(index, supplierIndex) {
        var value = parseInt($("#internationalNumberProductType" + supplierIndex).val());
        // if(value > 1) {
        $("#internationalProductTypeValues" + index + "" + supplierIndex).remove();
        $("#internationalNumberProductType" + supplierIndex).val(value - 1);
        fedex_internationalProductTypeCounter[supplierIndex] -= 1
        $("#fedexAddInternationalProductServiceType" + supplierIndex).show()
        // }
    }

    // Prodecura di inserimento di una nuova configurazione
    newFedexConfiguration=function(btn, index){
        var idfedex=$("#validfedex" + index).val();

        $(btn).ladda().ladda("start");

        // anche per brt, il codice cliente sarebbe il 'customer'
        var fedexClientCode=$("#fedexCustomer" + index).val();
        var fedexSeatCode=$("#fedexSeatCode" + index).val();
        var fedexApiKey=$("#fedexUser"+ index).val();
        var fedexSecretKey=$("#fedexPassword"+ index).val();
        var virtualShipperType = $('#virtualShipperType'+ index).val();
        var title = $('#title'+ index).val();
        var defaultLabelFormat=$("#defaultLabelFormatFedex" + index).val();
        var defaultTariff=$("#defaultTariffFedex" + index).val();
        var orderIdInNotes=$('#orderIdInNotesFedex' + index).is(':checked');

        // Lista servizi di spedizione nazionali e internazionali
        var productTypeList = [];
        $.each($('#fedexform' + index).serializeArray(), function(i, field) {
            if (field.name === ("nationalProductType[]" + index) || field.name === ("internationalProductType[]" + index))
                debugger
                debugger
                productTypeList.push(field.value);
        });

        if(productTypeList.length == 0) {
            swal({
                title: "Errore",
                text: "Seleziona almeno un servizio di spedizione.",
                type: "error"
            });
            $(btn).ladda("stop");
            return
        }

            $.ajax({
                url: "${createLink(controller: 'shipper', action:'save' )}",
                method: 'POST',
                data: {
                    id:idfedex,
                    type:30,
                    name: "FEDEX",
                    virtualShipperType: virtualShipperType,
                    title: title,
                    'fedexUserConfiguration.title': title,
                    'fedexUserConfiguration.customer':fedexClientCode,
                    'fedexUserConfiguration.user':fedexApiKey,
                    'fedexUserConfiguration.password': fedexSecretKey,
                    'fedexUserConfiguration.seatCode':fedexSeatCode,
                    'fedexUserConfiguration.defaultLabelFormat':defaultLabelFormat,
                    'fedexUserConfiguration.defaultTariff':defaultTariff,
                    'fedexUserConfiguration.orderIdInNotes': orderIdInNotes,
                    'productTypes[]': productTypeList,
                    storeID:${session['store_id']}
                },
                success: function(data){

                    $("#validfedex").val(data);

                    swal({title:"Salvataggio", text:"Modifiche verificate e salvate con successo", type:"success"},function(){
                        showTemplate('carriers', 'fedex', index);
                        $('#configuration-sda>img').removeClass("grayscale")
                        window["js-switch-sm-fedexdefault" ].enable();
                    });
                },
                error: function(xhr,status,error){
                    swal("Ops", xhr.responseText, "error")
                    $("#enabledfafedex").html('<a data-toggle="tooltip" class="checkfav" data-placement="left" title="Corriere non configurato"><i class="fa fa-check" style="color: red;"></i></a>');
                },
                complete: function(xhr,status,error){
                    $(btn).ladda("stop");
                }
            });
    }

    // Prcedura di salvataggio generico della configurazione
    // Stabiliamo se dobbiamo aggiornare o inserire una nuova config del corriere
    saveFedexConfiguration = function(btn, index) {
        if(!$('#fedexform'+ index).valid())
            return;

        var idfedex=$("#validfedex" + index).val();

        // Se id = 0 dobbiamo creare un nuova configurazione
        if(idfedex == 0){
            newFedexConfiguration(btn, index);
        // Altrimenti aggiorniamo la configurazione
        } else {
            $(btn).ladda().ladda("start");
            // anche per brt, il codice cliente sarebbe il 'customer'
            var fedexClientCode=$("#fedexCustomer" + index).val();
            var fedexSeatCode=$("#fedexSeatCode" + index).val();
            var fedexApiKey=$("#fedexUser" + index).val();
            var fedexSecretKey=$("#fedexPassword" + index).val();
            var defaultLabelFormat=$("#defaultLabelFormatFedex" + index).val();
            var defaultTariff=$("#defaultTariffFedex" + index).val();
            var orderIdInNotes=$('#orderIdInNotesFedex' + index).is(':checked');

            // Lista servizi di spedizione nazionali e internazionali
            var productTypeList = [];
            $.each($('#fedexform' + index).serializeArray(), function(i, field) {
                if (field.name === ("nationalProductType[]" + index) || field.name === ("internationalProductType[]" + index))
                    productTypeList.push(field.value);
                debugger
                debugger
            });

            if(productTypeList.length == 0) {
                swal({
                    title: "Errore",
                    text: "Seleziona almeno un servizio di spedizione.",
                    type: "error"
                });
                $(btn).ladda("stop");
                return
            }

            // perchè solo qua ci sono gli apici singoli?
            var virtualShipperType = $('#virtualShipperType' + index).val();
            var title = $('#title'+ index).val();

                $.ajax({
                    url: "${createLink(controller: 'shipper', action:'update' )}",
                    method: 'POST',
                    data: {
                        id:idfedex,
                        type:30,
                        name: "FEDEX",
                        virtualShipperType: virtualShipperType,
                        title: title,
                        'fedexUserConfiguration.title': title,
                        'fedexUserConfiguration.customer':fedexClientCode,
                        'fedexUserConfiguration.user':fedexApiKey,
                        'fedexUserConfiguration.password': fedexSecretKey,
                        'fedexUserConfiguration.seatCode':fedexSeatCode,
                        'fedexUserConfiguration.defaultLabelFormat':defaultLabelFormat,
                        'fedexUserConfiguration.defaultTariff':defaultTariff,
                        'fedexUserConfiguration.orderIdInNotes': orderIdInNotes,
                        'productTypes[]': productTypeList,
                        storeID:${session['store_id']}
                    },
                    success: function(data){

                        $("#validfedex").val(data);

                        swal({title:"Salvataggio", text:"Modifiche verificate e salvate con successo", type:"success"},function(){
                            showTemplate('carriers', 'fedex', index);
                            $('#configuration-sda>img').removeClass("grayscale")
                            window["js-switch-sm-fedexdefault" ].enable();
                        });
                    },
                    error: function(xhr,status,error){
                        swal("Ops", xhr.responseText, "error")
                        $("#enabledfafedex").html('<a data-toggle="tooltip" class="checkfav" data-placement="left" title="Corriere non configurato"><i class="fa fa-check" style="color: red;"></i></a>');
                    },
                    complete: function(xhr,status,error){
                        $(btn).ladda("stop");
                    }
                });
        }
    }



</script>
<div class="ibox" style="width: 100%">
    <div class="ibox-content">

        <div class="col-md-12">
            <cdn:img src="img/shipper/logo-write/brt.png" width="120"/>
        </div>

        <div class="col-md-12">
            <poleepo:showLimitCounter id="limitShipper" limit="${StoreLimits.MULTI_SHIPPER}" action="da creare" shipperType="${ShipperType.FEDEX}"/>
        </div>
        <div class="tabs-container m-t">
            <ul class="nav nav-tabs">
                <g:each var="i" in="${0..maxConfs}">
                    <g:set var="shipper" value="${shippers && shippers[i] ? shippers[i] : null}" />
                    <g:set var="enabledCondition" value="${(i == 0 || (shippers != null && shippers[i-1] != null))}" />
                    <g:set var="limitExceed" value="${multiLimit != StoreLimit.UNLIMITED_INTEGER && i >= multiLimit}"/>
                    <li class="${i == 0 ? 'active' : ''}" ${limitExceed ? "disabled=disabled style=cursor:not-allowed onclick=\$('#provaGratuitaModal').modal('show')" : enabledCondition ? "" : "disabled=disabled style=cursor:not-allowed"}>
                        <a ${limitExceed ? "style=pointer-events:none" : enabledCondition ? "href=#fedex${i+1}Configuration" : "style=pointer-events:none"} data-toggle="tab" aria-expanded="true">
                            ${shipper?.title ?: 'Configurazione FedEx ' + (i+1)}
                        </a>
                    </li>
                </g:each>
            </ul>

            <div class="tab-content">
                <g:each var="indice" in="${0..maxConfs}">
                    <g:set var="shipper" value="${shippers && shippers[indice] ? shippers[indice] : null}" />
                    <div id="fedex${indice+1}Configuration" class="tab-pane ${indice == 0 ? 'active' : ''}">
                        <div class="panel-body">
                            <form id="fedexform${indice}">

                                <div class="row m-b-lg">
                                    <div class="col-md-offset-6 col-md-6 text-right">
                                        <div id="switcheryDiv${indice}" class="input-group border-none pull-right" style="display: ${shipper==null?'none':'block'}">
                                            <label>Abilitato</label>
                                            <input id="activefedex${indice}" data-conf="configuration-fedex" datafld="${ShipperType.FEDEX}" onchange="setCarrier('${shipper?.id ?: 0}',$(this).is(':checked')?'true':'false',this, $('#virtualShipperType${indice}').val());" type="checkbox" class="form-control js-switch-green" ${shipper?.enable?'checked':''} />
                                        </div>
                                    </div>
                                </div>

                                <div class="row" style="margin-top:5px;">
                                    <input id="validfedex${indice}" style="display:none;" value="${shipper?.id?:0}" >
                                    <input type="hidden" name="virtualShipperType" id="virtualShipperType${indice}" value="${shipper?.virtualShipperType ?: ShipperType.getVirtualShipperType(ShipperType.FEDEX, indice + 1)}">

                                    <p>Shipper id value: ${shipper?.id?:0}</p>

                                    <!-- Codice Cliente: mappato a 'customer' -->
                                    <h3 class="col-sm-4">${g.message(code:'shipper.fedexUserConfiguration.customer')}</h3>
                                    <div class="col-sm-8">
                                        <div class="input-group">
                                            <input type="text" name="fedexCustomer" id="fedexCustomer${indice}" class="form-control"  value="${shipper?.customer}"  required="" >
                                            <span class="input-group-addon">
                                                <i class="fa fa-info-circle" data-toggle="tooltip" data-placement="left" title="Normalmente è un codice composto da 7 cifre numeriche"></i>
                                            </span>
                                        </div>
                                    </div>
                                </div>

                                <!-- Filiale di partenza: mappato a 'seatCode' -->
                                <div class="row" style="margin-top:5px;">
                                    <h3 class="col-sm-4">${g.message(code:'shipper.fedexUserConfiguration.seatCode')} (DEFAULT)</h3>
                                    <div class="col-sm-8">
                                        <div class="input-group">
                                            <input type="text" name="fedexUserConfiguration.seatCode" id="fedexSeatCode${indice}" class="form-control" required="required" value="${shipper?.seatCode}" required="" >
                                        </div>
                                    </div>
                                </div>

                                <!-- Chiave API: mappata a 'user' -->
                                <div class="row" style="margin-top:5px;">
                                    <h3 class="col-sm-4">${g.message(code:'shipper.fedexUserConfiguration.user')}</h3>
                                    <div class="col-sm-8">
                                        <div class="input-group">
                                            <input type="text" name="fedexUser" id="fedexUser${indice}" class="form-control" required="required" value="${shipper?.username}"  required="" >
                                        </div>
                                    </div>
                                </div>

                                <!-- Chiave Segreta: mappata a 'password' -->
                                <div class="row" style="margin-top:5px;">
                                    <h3 class="col-sm-4">${g.message(code:'shipper.fedexUserConfiguration.password')}</h3>
                                    <div class="col-sm-8">
                                        <div class="input-group">
                                            <input type="password" name="fedexPassword" id="fedexPassword${indice}" class="form-control" required="required" value="${shipper?.pswd}"  onmouseover="mouseoverPass(this);" onmouseout="mouseoutPass(this);" required="" >
                                        </div>
                                    </div>
                                </div>

                                <!-- Nome configurazione -->
                                <div class="row" style="margin-top: 5px">
                                    <h3 class="col-sm-4">
                                        ${g.message(code:'shipper.configuration.customTitle')}
                                        &nbsp;<i class="fa fa-info-circle text-primary"  data-toggle="tooltip" data-placement="right" title="Scegli un nome che ti aiuti a riconoscere questa configurazione!"></i>
                                    </h3>
                                    <div class="col-sm-8">
                                        <div class="input-group">
                                            <input type="text" name="fedexUserConfiguration.title" id="title${indice}" class="form-control" required="required" value="${shipper?.title ?: 'Configurazione FedEx ' + (indice+1)}" >
                                        </div>
                                    </div>
                                </div>
                                <hr/>

                                <!-- Tipo stampa predefinito: mappato a 'defaultLabelFormat' -->
                                <div class="row" style="margin-top:5px;">
                                    <h3 class="col-sm-4">Tipo stampa predefinito</h3>
                                    <div class="col-sm-8">
                                        <div class="input-group">
                                            <select name="fedexUserConfiguration.defaultLabelFormat" class="form-control" id="defaultLabelFormatFedex${indice}">
                                                <option value="PDF" ${shipper?.defaultLabelFormat=="ZPL"?"":"selected"}>PDF</option>
                                                <option value="ZPL" ${shipper?.defaultLabelFormat=="ZPL"?"selected":""}>ZEBRA</option>
                                            </select>
                                        </div>
                                    </div>
                                </div>

                                <!-- Codice tariffa predefinito: mappato a 'defaultTariff' -->
                                <div class="row" style="margin-top:5px;">
                                    <h3 class="col-sm-4">Codice tariffa predefinito</h3>
                                    <div class="col-sm-8">
                                        <div class="input-group">
                                            <input type="text" name="fedexUserConfiguration.defaultTariff" class="form-control" id="defaultTariffFedex${indice}" value="${shipper?.defaultTariff}" maxlength="3"/>
                                        </div>
                                    </div>
                                </div>

                                <!-- ID ordine nelle note: mappato a 'orderIdInNotesBrt' -->
                                <div class="row" style="margin-top:5px;">
                                    <h3 class="col-sm-4">ID ordine nelle note</h3>
                                    <div class="col-sm-8">
                                        <input type="checkbox" name="fedexUserConfiguration.orderIdInNotesBrt" class="form-control js-switch-green" id="orderIdInNotesFedex${indice}" ${shipper?.orderIdInNotes?'checked':''}/>
                                    </div>
                                </div>

                                <hr/>
                                <!-- Tipo spedizione -->
                                <div class="row m-t-sm">
                                    <div class="col-sm-6">
                                        Servizi nazionali
                                        <div id="nationalProductTypeForm${indice}">
                                            <input type="hidden" id="nationalNumberProductType${indice}" value="${shipper && shipper.nationalProductTypeStored ? shipper.nationalProductTypeStored.size() : 1}"/>
                                            <g:each var="productType" in="${shipper && shipper.nationalProductTypeStored ? shipper.nationalProductTypeStored : []}" status="nationalProductTypesIndex">
                                                <div id="nationalProductTypeValues${nationalProductTypesIndex}${indice}" class="row m-t-sm">
                                                    <div class="col-sm-10">
                                                        <input type="hidden" name="index" value="${nationalProductTypesIndex}"/>
                                                        <div class="input-group">
                                                            <g:select class="form-control" name="nationalProductType[]${indice}"
                                                                      from="${nationalProductTypes}"
                                                                      value="${productType.code}"
                                                                      optionValue="description"
                                                                      optionKey="code"
                                                                      required="required"/>
                                                        </div>
                                                    </div>
                                                    <div class="col-sm-2">
                                                        <a onclick="fedex_deleteNationalProductType(${nationalProductTypesIndex}, ${indice})"><i class="fa fa-trash fa-2x"></i></a>
                                                    </div>
                                                </div>
                                            </g:each>
                                        </div>
                                        <div class="row">
                                            <div id="fedexAddNationalProductServiceType${indice}" class="col-sm-2 m-t">
                                                <a class="btn btn-default btn-block" onclick="fedex_addNationalProductType(${indice})"><i class="fa fa-plus"></i></a>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-sm-6">
                                        Servizi internazionali
                                        <div id="internationalProductTypeForm${indice}">
                                            <input type="hidden" id="internationalNumberProductType${indice}" value="${shipper && shipper.internationalProductTypeStored ? shipper.internationalProductTypeStored.size() : 1}"/>
                                            <g:each var="productType" in="${shipper && shipper.internationalProductTypeStored ? shipper.internationalProductTypeStored : []}" status="i">
                                                <div id="internationalProductTypeValues${i}${indice}" class="row m-t-sm">
                                                    <div class="col-sm-10">
                                                        <input type="hidden" name="index" value="${i}"/>
                                                        <div class="input-group">
                                                            <g:select class="form-control" name="internationalProductType[]${indice}"
                                                                      from="${internationalProductTypes}"
                                                                      value="${productType.code}"
                                                                      optionValue="description"
                                                                      optionKey="code"
                                                                      required="required"/>
                                                        </div>
                                                    </div>
                                                    <div class="col-sm-2">
                                                        <a onclick="fedex_deleteInternationalProductType(${i}, ${indice})"><i class="fa fa-trash fa-2x"></i></a>
                                                    </div>
                                                </div>
                                            </g:each>
                                        </div>
                                        <div class="row">
                                            <div id="fedexAddInternationalProductServiceType" class="col-sm-2 m-t">
                                                <a class="btn btn-default btn-block" onclick="fedex_addInternationalProductType(${indice})"><i class="fa fa-plus"></i></a>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <hr/>

                            </form>


                            <g:if test="${shipper != null && shipper.enable == true}">
                                <hr />
                                <div class="row">
                                    <div class="col-md-12">
                                        <a onclick='$("#fedexApiConfiguration${indice}").slideToggle();$(this).children().toggle()'>
                                            <h3><i class="fa fa-chevron-right"></i> <g:message code="carrier.configuration.api" /></h3>
                                            <h3 style="display:none"><i class="fa fa-chevron-down"></i> <g:message code="carrier.configuration.api" /></h3>
                                        </a>
                                    </div>
                                </div>

                                <div id="fedexApiConfiguration${indice}" style="display: none">

                                    <div class="row">
                                        <div class="col-md-4">
                                            <div class="form-group">
                                                <label><g:message code="carrier.configuration.code" /></label>
                                                <div class="input-group">
                                                    %{--                                            <span class="input-group-addon"><i class="fas fa-project-diagram"></i></span>--}%
                                                    <input type="text" readonly class="form-control" value="${shipper?.virtualShipperType}" />
                                                    <span class="input-group-btn">
                                                        <a class="btn btn-primary pull-right" onclick="copyToClipboard('${shipper?.virtualShipperType}');" data-toggle="tooltip" title="${g.message(code: 'copy')}">
                                                            <i class="fas fa-copy"></i> <g:message code="copy" />
                                                        </a>
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <g:if test="${shipper != null && shipper.virtualShipperType == ShipperType.getVirtualShipperType(ShipperType.FEDEX, 1)}">
                                        <div class="row m-t">
                                            <div class="col-md-12">
                                                <span><g:message code="carrier.configuration.info" /></span>
                                            </div>
                                        </div>
                                    </g:if>
                                </div>
                            </g:if>

                            <div class="row">
                                <div class="col-sm-12">

                                    <button class="btn btn-primary pull-right ladda-button" data-style="expand-left" id="fedexsave" onclick="saveFedexConfiguration(this, ${indice})" style=" margin:5px;">Salva</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </g:each>
            </div>

        </div>

        <br/>

        <div class="conf-form">

            <!-- STATO CONFIGURAZIONE -->
            <div class="row">
                <div class="col-md-12">

                    <g:if test="${flash.glsmessage}">
                        <div class="alert alert-info">${flash.glsmessage}</div>
                    </g:if>
                    <g:if test="${flash.glserror}">
                        <div class="al --}%ert alert-danger">${flash.glserror}</div>
                    </g:if>
                </div>
            </div>


            <!-- FORM -->
            <div class="row">
                <div class="col-md-12">
                    <g:if test="${shipper && shipper.shipperDefault}">
                        <div class="alert alert-info" align="justify" style="margin-bottom:20px">${shipper.name} è stato impostato come corriere di default, durante la pubblicazione dei prodotti verrà indicato come il corriere che si occuperà della spedizione per i marketplace che lo richiedono. Potrai cambiarlo configurando e impostando come default un altro corriere.</div>
                    </g:if>

                    <div class="row m-b" style="margin-top:15px;">
                        <div class="col-sm-12">
                            <a onclick='$("#fedexconfiguration").slideToggle();$(this).children().toggle()'>
                                <h3><i class="fa fa-plus-circle"></i> Dove posso trovare i dati richiesti?</h3>
                                <h3 style="display:none"><i class="fa fa-minus-circle"></i> Dove posso trovare i dati richiesti?</h3>
                            </a>
                        </div>
                    </div>
                    <div id="fedexconfiguration" style="margin-top:5px; display:none">
                        <p align="justify">
                            I dati necessari alla configurazione di Fedez sono normalmente forniti dal corriere stesso.
                        <ul>
                            <li>Nel campo "<i>${g.message(code:'shipper.fedexUserConfiguration.customer')}</i>" va inserito il codice cliente, tipicamente &egrave; composto da 7 caratteri numerici.</li>
                            <li>I campi "<i>${g.message(code:'shipper.fedexUserConfiguration.user')}</i>" e "<i>${g.message(code:'shipper.fedexUserConfiguration.password')}</i>" devono essere richiesti a FEDEX specificando che si necessita di un'utenza per l'integrazione tramite API di FEDEX.</li>
                            <li>La modalit&agrave; di lavoro "<i>AutoConferma</i>" deve essere decisa in fase di attivazione con il personale BRT. Con questa modalit&agrave; operativa, nel momento in cui si affidano le spedizioni a FEDEX, queste spedizioni sono direttamente confermabili dalla filiale di gestione.</li>
                        </ul>
                    </p>

                    </div>
                </div>
            </div>
        </div>

    </div>



</div>
