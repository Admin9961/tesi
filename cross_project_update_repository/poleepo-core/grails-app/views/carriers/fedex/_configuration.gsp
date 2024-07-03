<%@ page import="com.macrosolution.mpm.store.StoreLimit; com.macrosolution.mpm.claim.StoreLimits; com.macrosolution.mpm.shipper.ShipperType;" contentType="text/html;charset=UTF-8" %>
<g:set var="maxConfs" value="${2}"/>
<g:set var="indexToOpen" value="${Long.parseLong(params.confOffset ?: "0")}"/>
<g:set var="multiLimit" value="${poleepo.getLimit([limit: StoreLimits.MULTI_SHIPPER])}"/>

<g:set var="shipper" value="${shippers && shippers[0] ? shippers[0] : null}" />

<script type="text/javascript">

    $(document).ready(function(){
        setUpPage();
    });

    // Prodecura di inserimento di una nuova configurazione
    newFedexConfiguration=function(btn, index){
        var idfedex=$("#validfedex" + index).val();

        $(btn).ladda().ladda("start");

        // anche per brt, il codice cliente sarebbe il 'customer'
        var fedexClientCode=$("#fedexCustomer" + index).val();
        var fedexApiKey=$("#fedexUser"+ index).val();
        var fedexSecretKey=$("#fedexPassword"+ index).val();
        var virtualShipperType = $('#virtualShipperType'+ index).val();
        var title = $('#title'+ index).val();

        if($("#fedexform").valid() ){
            $.ajax({
                url: "${createLink(controller: 'shipper', action:'save' )}",
                method: 'POST',
                data: {
                    id:idfedex,
                    type:30,
                    name: "FEDEX",
                    virtualShipperType: virtualShipperType,
                    title: title,
                    'fedexUserConfiguration.customer':fedexClientCode,
                    'fedexUserConfiguration.user':fedexApiKey,
                    'fedexUserConfiguration.password': fedexSecretKey,
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

    // Prcedura di salvataggio generico della configurazione
    // Stabiliamo se dobbiamo aggiornare o inserire una nuova config del corriere
    saveFedexConfiguration = function(btn, index) {

        debugger
        debugger
        if(!$('#fedexform').valid())
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
            var fedexApiKey=$("#fedexUser" + index).val();
            var fedexSecretKey=$("#fedexPassword" + index).val();

            // perchè solo qua ci sono gli apici singoli?
            var virtualShipperType = $('#virtualShipperType' + index).val();
            var title = $('#title'+ index).val();

            if($("#fedexform").valid() ){
                $.ajax({
                    url: "${createLink(controller: 'shipper', action:'update' )}",
                    method: 'POST',
                    data: {
                        id:idfedex,
                        type:30,
                        name: "FEDEX",
                        virtualShipperType: virtualShipperType,
                        title: title,
                        'fedexUserConfiguration.customer':fedexClientCode,
                        'fedexUserConfiguration.user':fedexApiKey,
                        'fedexUserConfiguration.password': fedexSecretKey,
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
    }



</script>
//TODO: continua da qua ad aggiungere il milti page

<div class="ibox" style="width: 100%">
    <div class="ibox-content">
        <div class="conf-activate">
            <div class="row">
                <div class="col-md-6">
                    <cdn:img src="img/shipper/logo-write/sda.png" width="90"/>
                </div>
                <div class="col-md-6 text-right">
                    <div id="switcheryDiv" class="input-group border-none pull-right" style="display: ${shipper==null?'none':'block'}">
                        <label>Abilitato</label>
                        <input id="activefedex" data-conf="configuration-fedex" datafld="${ShipperType.FEDEX}" onchange="setCarrier('${shipper?.id ?: 0}',$(this).is(':checked')?'true':'false',this, $('#virtualShipperType').val());" type="checkbox" class="form-control js-switch-green" ${shipper?.enable?'checked':''} />
                    </div>
                </div>
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
                    <g:if test="${shipper && shipper?.shipperDefault}">
                        <div class="alert alert-info" align="justify" style="margin-bottom:20px">${shipper.carrier_type} è stato impostato come corriere di default, durante la pubblicazione dei prodotti verrà indicato come il corriere che si occuperà della spedizione per i marketplace che lo richiedono. Potrai cambiarlo configurando e impostando come default un altro corriere.</div>
                    </g:if>
                    <form id="fedexform">

                        <div class="row" style="margin-top:5px;">
                            <input id="validfedex" style="display:none;" value="${shipper?.id?:0}" >
                            <input type="hidden" name="virtualShipperType" id="virtualShipperType" value="${shipper?.virtualShipperType ?: ShipperType.getVirtualShipperType(ShipperType.FEDEX , 1)}">
                            <input type="hidden" name="title" id="title" value="${shipper?.title ?: ShipperType.FEDEX}">

                            <p>Shipper id value: ${shipper?.id?:0}</p>

                            <!-- Codice Cliente: senderAccID -->
                            <h3 class="col-sm-4">${g.message(code:'shipper.fedexUserConfiguration.customer')}</h3>
                                <div class="col-sm-8">
                                    <div class="input-group">
                                        <input type="text" name="fedexCustomer" id="fedexCustomer" class="form-control"  value="${shipper?.customer}"  required="" >
                                        <span class="input-group-addon">
                                            <i class="fa fa-info-circle" data-toggle="tooltip" data-placement="right" title="Codice cliente"></i>
                                        </span>
                                    </div>
                                </div>
                        </div>

                        <!-- Chiave API: user -->
                        <div class="row" style="margin-top:5px;">
                            <h3 class="col-sm-4">${g.message(code:'shipper.fedexUserConfiguration.user')}</h3>
                            <div class="col-sm-8">
                                <div class="input-group">
                                    <input type="text" name="fedexUser" id="fedexUser" class="form-control" required="required" value="${shipper?.username}" required="" >
                                </div>
                            </div>
                        </div>

                        <!-- Chiave Segreta: password -->
                        <div class="row" style="margin-top:5px;">
                            <h3 class="col-sm-4">${g.message(code:'shipper.fedexUserConfiguration.password')}</h3>
                            <div class="col-sm-8">
                                <div class="input-group">
                                    <input type="text" name="fedexPassword" id="fedexPassword" class="form-control" required="required" value="${shipper?.pswd}"  required="" >
                                </div>
                            </div>
                        </div>
                        <hr/>
                        <div class="row" style="margin-top:5px;">
                            <h3 class="col-sm-4">Formato stampa predefinito</h3>
                            <div class="col-sm-8">
                                <div class="input-group">
                                    <select name="fedexUserConfiguration.defaultLabelFormat" class="form-control" id="defaultLabelFormatFedex">
                                        <option value="A4" ${shipper?.defaultLabelFormat=="A6"?"":"selected"}>A4</option>
                                        <option value="A6" ${shipper?.defaultLabelFormat=="A6"?"selected":""}>A6</option>
                                    </select>
                                </div>
                            </div>
                        </div>
                        <hr/>
                    </form>

                    <g:if test="${shipper != null && shipper.enable == true}">
                        <hr />
                        <div class="row">
                            <div class="col-md-12">
                                <a onclick='$("#sdaApiConfiguration").slideToggle();$(this).children().toggle()'>
                                    <h3><i class="fa fa-chevron-right"></i> <g:message code="carrier.configuration.api" /></h3>
                                    <h3 style="display:none"><i class="fa fa-chevron-down"></i> <g:message code="carrier.configuration.api" /></h3>
                                </a>
                            </div>
                        </div>

                        <div id="sdaApiConfiguration" style="display: none">

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
                            <button class="btn btn-primary pull-right ladda-button" data-style="expand-left" id="fedexsave" onclick="saveFedexConfiguration(this)" style="margin:5px;">Salva</button>
                        </div>
                    </div>
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
                            I dati necessari alla configurazione di sda sono normalmente forniti dal corriere stesso.
                        <ul>
                            <li>Nel campo "<i>${g.message(code:'shipper.sdaUserConfiguration.sdasenderAccId')}</i>" va inserito il numero di contratto, tipicamente &egrave; composto da otto caratteri numerici.</li>
                            <!--<li>Il "<i>${g.message(code:'shipper.sdaUserConfiguration.customer')}</i>" &egrave; lo stesso che usi per accedere a mysda</li>-->
                            <li>I campi "<i>${g.message(code:'shipper.sdaUserConfiguration.user')}</i>" e "<i>${g.message(code:'shipper.sdaUserConfiguration.password')}</i>" devono essere richiesti a sda specificando che si necessita di un'utenza per l'integrazione tramite API di sda.</li>
                        </ul>
                    </p>
                    </div>
                </div>
            </div>
        </div>

    </div>



</div>
