<%@ page import="com.macrosolution.mpm.shipper.ShipperType;" contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <meta name="layout" content="main"/>
    <title><poleepo:applicationTitle title="Corrieri"/></title>

    <cdn:css href="css/datepicker/datepicker3.min.css" />
    <cdn:js src="js/datepicker/bootstrap-datepicker.min.js" />

    <cdn:js src="js/clockpicker/clockpicker.min.js"/>
    <cdn:css href="css/clockpicker/clockpicker.min.css" />

    <cdn:js src="js/MSValidationRules.js"/>
    <cdn:js src="js/commonFunctions.js"/>

    <script type="text/javascript">
        $(document).ready(function(){
            setUpPage();
            $('.js-switch-green-sm').on('change' , function(e) {
                if(setSwitchery(this)) {
                    resetOldSwitch(this);
                    setToolTipSwitchery(this);
                }
            });
        });

        function setToolTipSwitchery(current) {
            $('ul.configuration-list').find("input").each(function() {
                $(this).closest('span').attr("data-original-title","Puoi impostare questo corriere come corriere di default");
            });
            $(".sub-configuration .configuration-switchery").each(function() {
                $(this).closest('span').attr("data-original-title","Puoi impostare questo corriere come corriere di default");
            });
            $(current).closest('span').attr("data-original-title","Corriere di default");

            let dftParent = $(current).attr("data-parent");
            if(dftParent != null && window["js-switch-sm-" + dftParent] != null && !window["js-switch-sm-" + dftParent].isChecked()) {
                window["js-switch-sm-" + dftParent].setPosition(true);
                $(dftParent).attr("data-original-title","Corriere di default");
                window["js-switch-sm-" + $(this).attr("id")].disable();
            }
        }

        function resetOldSwitch(current) {
            let dftParent = $(current).attr("data-parent");
            $("ul.configuration-list").find("input:checked").each(function(e) {
                if(this !== current && dftParent !== $(this).attr("id")) {
                    window["js-switch-sm-" + $(this).attr("id")].setPosition(true);

                    if($(this).attr("data-disabled") !== "true")
                        window["js-switch-sm-" + $(this).attr("id")].enable();
                }
            });

            $(".sub-configuration .configuration-switchery:checked").each(function(e) {
                if(this !== current) {
                    window["js-switch-sm-" + $(this).attr("id")].setPosition(true);

                    if($(this).attr("data-disabled") !== "true")
                        window["js-switch-sm-" + $(this).attr("id")].enable();
                }
            })
        }

        function setSwitchery(current) {
            var result;
            if ($(current).is(':checked')) {
                $.ajax({
                    url: "${createLink(controller: 'shipper', action:'shipperDefault')}",
                    data: {shipperType: $(current).attr('datafld')},
                    method: 'POST',
                    async:false,
                    success: function(data) {
                        if ($(current).attr('datafld') === "SPEDISCI_ONLINE") {
                            toastr.success("Hai impostato Spedisci .Online come corriere di default");
                        }
                        else {
                            toastr.success("Hai impostato " + $(current).attr('datafld') + " come corriere di default");
                        }
                        window["js-switch-sm-" + $(current).attr("id")].disable();
                        result= true;
                    },
                    error: function(xhr,status,error){
                        swal("Ops", xhr.responseText, "error");
                        window["js-switch-sm-" + $(current).attr("id")].setPosition(true);
                        result= false;
                    },
                });
                return result;
            }
        }

        function showTemplate(category, sourceName, index = 0) {
            $('#configurationContainer').removeClass('size-small');
            $.ajax({
                url: '${createLink(controller: 'carriers', action: 'showTemplate')}?template=' + sourceName + '&category=' + category + '&confOffset=' + index,
                method: 'GET',
                success: function(data) {
                    $('#configurationContainer').html(data);
                }
            })
        }

        function mouseoverPass(obj) {
            obj.type = "text";
        }

        function mouseoutPass(obj) {
            obj.type = "password";
        }

        function setCarrier(id,value,switchery, virtualShipperType) {
            $.ajax({
                url:"${createLink(controller: "carriers", action:'setEnable')}",
                data: {
                    'id':id,
                    'enable':value,
                    'shipperType': $(switchery).attr('datafld'),
                    virtualShipperType: virtualShipperType
                },
                method:'POST',
                success: function(data) {
                    let name = $(switchery).attr("data-name") || $(switchery).attr('datafld');
                    toastr.success("Hai impostato " + name + " come "+ ($(switchery).is(':checked')?'abilitato':'disabilitato'));

                    if($(switchery).is(':checked')) {
                        $('#' + $(switchery).attr("data-conf") + '>img').removeClass("grayscale")
                    }
                    else {
                        $('#' + $(switchery).attr("data-conf") + '>img').addClass("grayscale")
                    }

                    let shipperLimit = $('#countdownValue-limitShipper');
                    if (shipperLimit) {
                        let limit = parseInt(shipperLimit.text());
                        if($(switchery).is(':checked')) {
                            $('#countdownValue-limitShipper').text(limit - 1);
                        }
                        else {
                            $('#countdownValue-limitShipper').text(limit + 1);
                        }
                    }

                    if(data.id!== id) {
                        resetOldSwitch(switchery);

                        var id=data.switchery+"default";

                        setToolTipSwitchery($('#'+id));

                        window["js-switch-sm-" + id].setPosition(true);
                        window["js-switch-sm-" + id].disable();
                    }
                },
                error: function(xhr,status,error){
                    swal("Ops", xhr.responseText, "error");

                }

            })
        }
        function setErrorAdress (id,value,switchery){
            $.ajax({
                url:"${createLink(controller: "carriers", action:'setErrorAddress')}",
                data:{'id':id,'enable':value,'shipperType': $(switchery).attr('datafld')},
                method:'POST',
                success: function(data) {
                    let name = $(switchery).attr("data-name") || $(switchery).attr('datafld');
                    toastr.success("Hai impostato su " + name + " il controllo dell'indirizzo ");



                },
                error: function(xhr,status,error){
                    swal("Ops", xhr.responseText, "error");

                }

            })
        }
    </script>
</head>

<body>
<div id="configurationTabContainer">
    <div class="row page-heading white-bg" id="configurationList">
        <ul class="list-unstyled" id="salesChannelList">
            <li id="carriers">
                <span>Corrieri</span>
                <ul class="list-unstyled configuration-list" id="carriersList">
                    <li id="gls">
                        <a id="configuration-gls" href="#" onclick="showTemplate('carriers', 'gls')">
                            <cdn:img src="img/shipper/logo/gls.png" width="30" height="30" class="configuration-avatar ${glsconf?.enable?'':'grayscale'}"/>
                            <h2 class="configuration-item">GLS</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${glsconf?.shipperDefault ? 'Corriere di default' : 'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" datafld="${ShipperType.GLS}" ${glsconf?.shipperDefault?'checked':''} id="GLSdefault" class="js-switch-green-sm configuration-switchery" ${glsconf?'':'disabled'} ${glsconf?.shipperDefault?'disabled':''}/>
                        </span>
                    </li>
                    <li id="tnt">
                        <a id="configuration-tnt" href="#" onclick="showTemplate('carriers', 'tnt')">
                            <cdn:img src="img/shipper/logo/tnt.png" width="30" height="30" class="configuration-avatar ${tntconf?.enable?'':'grayscale'}"/>
                            <h2 class="configuration-item">Tnt Express</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${tntconf?.shipperDefault?'Corriere di default':'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" datafld="${ShipperType.TNT}" ${tntconf?.shipperDefault?'checked':''} id="TNTdefault" class="js-switch-green-sm configuration-switchery" ${tntconf?'':'disabled'} ${tntconf?.shipperDefault?'disabled':''}/>
                        </span>
                    </li>
                    <li id="sda">
                        <a id="configuration-sda" href="#" onclick="showTemplate('carriers', 'sda')">
                            <cdn:img src="img/shipper/logo/sda.png" width="30" height="30" class="configuration-avatar ${sdaconf?.enable?'':'grayscale'}"/>
                            <h2 class="configuration-item">SDA</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${sdaconf?.shipperDefault?'Corriere di default':'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" datafld="${ShipperType.SDA}" ${sdaconf?.shipperDefault?'checked':''} id="SDAdefault" class="js-switch-green-sm configuration-switchery" ${sdaconf?'':'disabled'} ${sdaconf?.shipperDefault?'disabled':''}/>
                        </span>
                    </li>
                    <li id="poste">
                        <a id="configuration-poste" href="#" onclick="showTemplate('carriers', 'poste')">
                            <g:set var="posteEnabled" value="${posteconf?.enable || posteDeliveryconf?.enable}"/>
                            <g:set var="posteDefault" value="${posteconf?.shipperDefault || posteDeliveryconf?.shipperDefault}"/>

                            <cdn:img src="img/shipper/logo/poste.png" width="30" height="30" class="configuration-avatar ${posteEnabled?'':'grayscale'}"/>
                            <h2 class="configuration-item">Poste Italiane</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${posteDefault?'Corriere di default':'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" datafld="${ShipperType.POSTE}" ${posteDefault?'checked':''} id="POSTEdefault" class="js-switch-green-sm configuration-switchery" disabled data-disabled="true" />
                        </span>
                    </li>
                    <li id="brt">
                        <a id="configuration-brt" href="#" onclick="showTemplate('carriers', 'brt')">
                            <cdn:img src="img/shipper/logo/brt.png" width="30" height="30" class="configuration-avatar ${brtconf?.enable?'':'grayscale'}"/>
                            <h2 class="configuration-item">BRT</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${brtconf?.shipperDefault?'Corriere di default':'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" datafld="${ShipperType.BRT}" ${brtconf?.shipperDefault?'checked':''} id="BRTdefault" class="js-switch-green-sm configuration-switchery" ${brtconf?'':'disabled'} ${brtconf?.shipperDefault?'disabled':''} />
                        </span>
                    </li>
                    <li id="dhl">
                        <a id="configuration-dhl" href="#" onclick="showTemplate('carriers', 'dhl')">
                            <cdn:img src="img/shipper/logo/dhl.png" width="30" height="30" class="configuration-avatar ${dhlconf?.enable?'':'grayscale'}"/>
                            <h2 class="configuration-item">DHL</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${dhlconf?.shipperDefault?'Corriere di default':'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" datafld="${ShipperType.DHL}" ${dhlconf?.shipperDefault?'checked':''} id="DHLdefault" class="js-switch-green-sm configuration-switchery" ${dhlconf?'':'disabled'} ${dhlconf?.shipperDefault?'disabled':''}/>
                        </span>
                    </li>
                    <li id="mit">
                        <a id="configuration-mit" href="#" onclick="showTemplate('carriers', 'mit')">
                            <cdn:img src="img/shipper/logo/mit.png" width="30" height="30" class="configuration-avatar ${mitconf?.enable?'':'grayscale'}"/>
                            <h2 class="configuration-item">MIT</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${mitconf?.shipperDefault?'Corriere di default':'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" datafld="${ShipperType.MIT}" ${mitconf?.shipperDefault?'checked':''} id="MITdefault" class="js-switch-green-sm configuration-switchery" ${mitconf?'':'disabled'} ${mitconf?.shipperDefault?'disabled':''}/>
                        </span>
                    </li>
                    <li id="spediscionline">
                        <a id="configuration-spediscionline" href="#" onclick="showTemplate('carriers', 'spediscionline')">
                            <cdn:img src="img/shipper/logo/spedisci_online.png" width="30" height="30" class="configuration-avatar ${spedisciOnlineconf?.enable ? '' : 'grayscale'}"/>
                            <h2 class="configuration-item">Spedisci.Online</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${spedisciOnlineconf?.shipperDefault ? 'Corriere di default' : 'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" dataFld="${ShipperType.SPEDISCI_ONLINE}" ${spedisciOnlineconf?.shipperDefault ? 'checked' : ''} id="spediscionlinedefault" class="js-switch-green-sm configuration-switchery" ${spedisciOnlineconf ? '' : 'disabled'} ${spedisciOnlineconf?.shipperDefault ? 'disabled' : ''}/>
                        </span>
                    </li>
                    <li id="qapla">
                        <a id="configuration-qapla" href="#" onclick="showTemplate('carriers', 'qapla')">
                            <cdn:img src="img/shipper/logo/qapla.png" width="30" height="30" class="configuration-avatar ${qaplaconf?.enable ? '' : 'grayscale'}"/>
                            <h2 class="configuration-item">Qapla'</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${qaplaconf?.shipperDefault ? 'Corriere di default' : 'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" dataFld="${ShipperType.QAPLA}" ${qaplaconf?.shipperDefault ? 'checked' : ''} id="qapladefault" class="js-switch-green-sm configuration-switchery" ${qaplaconf ? '' : 'disabled'} ${qaplaconf?.shipperDefault ? 'disabled' : ''}/>
                        </span>
                    </li>
                    <li id="fedex">
                        <a id="configuration-fedex" href="#" onclick="showTemplate('carriers', 'fedex')">
                            <cdn:img src="" width="30" height="30" class="configuration-avatar ${fedexconf?.enable ? '' : 'grayscale'}"/>
                            <h2 class="configuration-item">FedEx</h2>
                        </a>
                        <span class="configuration-switch" data-toggle="tooltip" title="${fedexconf?.shipperDefault ? 'Corriere di default' : 'Puoi impostare questo corriere come corriere di default'}" data-placement="right">
                            <input type="checkbox" dataFld="${ShipperType.FEDEX}" ${fedexconf?.shipperDefault ? 'checked' : ''} id="fedexdefault" class="js-switch-green-sm configuration-switchery" ${fedexconf ? '' : 'disabled'} ${fedexconf?.shipperDefault ? 'disabled' : ''}/>
                        </span>
                    </li>

                </ul>
            </li>
            <li id="automatic">
                <span>Operazioni Automatiche</span>
                <ul class="list-unstyled configuration-list" id="automaticList">
                    <li id="">
                        <poleepo:anchorLock notClaim="ORD_SHIPPING_AUTO" id="configuration-op" href="#" forcedAction="showTemplate('carriers', 'automatic_op')" onclick="showTemplate('carriers', 'automatic_op')" style="align-items: center;">
                            <h2 class="configuration-item">LDV Automatiche</h2>
                        </poleepo:anchorLock>
                    </li>
                </ul>
            </li>
        </ul>
    </div>

    <div id="configurationContainer" class="m-l-lg size-small">
        <div class="ibox">
            <div class="ibox-content">
                <div class="row configuration-preview">
                    <poleepo:isPoleepo>
                        <div class="col-sm-3">
                            <cdn:img src="img/poleepo/logo-preview.jpg"/>
                        </div>
                        <div class="col-sm-8">
                            <h3>Configurazioni Corrieri</h3>
                            <p class="text-bigger">Seleziona un corriere per gestirne le configurazioni.</p>

                        </div>
                    </poleepo:isPoleepo>
                    <poleepo:notPoleepo>
                        <div class="col-sm-12 text-center">
                            <h3>Configurazioni Corrieri</h3>
                            <p class="text-bigger">Seleziona un corriere per gestirne le configurazioni.</p>

                        </div>
                    </poleepo:notPoleepo>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
