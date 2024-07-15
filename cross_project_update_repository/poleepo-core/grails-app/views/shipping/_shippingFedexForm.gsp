<%@ page import = "com.macrosolution.mpm.shipping.Parcel" %>
<%@ page import = "com.macrosolution.mpm.shipper.fedex.FedexProductType" %>
<%@ page import = "com.macrosolution.mpm.shipping.fedex.FedexBoxType" %>


<cdn:css href="css/datepicker/datepicker3.min.css" />
<cdn:js src="js/datepicker/bootstrap-datepicker.min.js" />

<g:set var="shipId" value="${shipping?.id ?: 0}"></g:set>

<script type="text/javascript">

    $(document).ready(function(){
        changeLocation()
    })

    /**
     * Funzione per aggiungere un collo alla spedizione
     * */
    function addParcel${shipId}() {
        /* aggiorno il numero dei colli */
        let num = parseInt($("#numberPackage${shipId}").val());
        $("#numberPackage${shipId}").val(num + 1);

        if(contatoreParcel == 0)
            contatoreParcel = num;
        else
            contatoreParcel++;

        /* aggiungo l'html del collo */
        $("<div/>", {id: "parcel${shipId}_" + contatoreParcel, class:"row no-margins"}).appendTo("#packageform${shipId}");
        $("<input/>", {type:"hidden", name:"index", value: contatoreParcel}).appendTo("#parcel${shipId}_" + contatoreParcel);
        $("<div/>", {class:"col-sm-4"})
            .append($("<div/>", {class:"form-group"})
                .append($("<label/>", {text:"Peso (in Kg)"}))
                .append($("<div/>",{class:"input-group"})
                    .append($("<input/>", {type:"number", class:"form-control", name:"parcel.weight" + contatoreParcel, min:"0.01", required:"required", style:"text-align: center;"})))
            )
            .appendTo("#parcel${shipId}_" + contatoreParcel);
        $("<div/>", {class:"col-sm-5 col-sm-offset-1"})
            .append($("<div/>", {class:"form-group"})
                .append($("<label/>", {text:"Tipo pacco"}))
                .append($("<div/>",{class:"input-group"})
                    .append($("<select/>", {class:"form-control", name:"parcel.boxType" + contatoreParcel})
                        <g:each var="opt" in="${FedexBoxType.list()}">
                            .append($("<option/>", {value:"${opt.id}", text:"${opt.label}"}))
                        </g:each>
                    )
                ))
            .appendTo("#parcel${shipId}_" + contatoreParcel);
        $("<div/>" , {class:"col-sm-2 m-t"})
            .append($("<a/>", {onclick:"deleteParcel${shipId}(" + contatoreParcel + ")"})
                .append($("<i/>", {class:"fa fa-trash fa-2x m-t"}))
            )
            .appendTo("#parcel${shipId}_" + contatoreParcel)


        addDimensions${shipId}(contatoreParcel);
    }

    function addDimensions${shipId}(num) {
        parcelWidth = "";
        parcelHeight = "";
        parcelDepth = "";
        if($("#numberPackage${shipId}").val() == "1") {
            parcelWidth = "${parcelWidth}";
            parcelHeight = "${parcelHeight}";
            parcelDepth = "${parcelDepth}";
        }
        $("<div/>",{class:"col-sm-12 dimensions"})
            .append($("<div/>", {class: "form-group", style: "margin-bottom: 0px"})
                .append($("<label/>", {text: "Dimensioni in centimetri"})))
            .appendTo("#parcel${shipId}_" + num);

        $("<div/>", {class: "col-sm-3 dimensions"})
            .append($("<div/>",{class:"form-group"})
                .append($("<label/>",{text:"Larghezza"}))
                .append($("<div/>",{class:"input-group"})
                    .append($("<input/>",{type:"number",class:"form-control", name:"parcel.width"+num, min:"0.01", value: parcelWidth})))
            )
            .appendTo("#parcel${shipId}_" + num);

        $("<div/>",{class:"col-sm-3 col-sm-offset-1 dimensions"})
            .append($("<div/>",{class:"form-group "})
                .append($("<label/>",{text:"Altezza"}))
                .append($("<div/>",{class:"input-group"})
                    .append($("<input/>",{type:"number",class:"form-control", name:"parcel.height"+num, min:"0.01", value: parcelHeight})))
            )
            .appendTo("#parcel${shipId}_" + num);

        $("<div/>",{class:"col-sm-3 col-sm-offset-1 dimensions"})
            .append($("<div/>",{class:"form-group"})
                .append($("<label/>",{text:"Profondità"}))
                .append($("<div/>",{class:"input-group"})
                    .append($("<input/>",{type:"number",class:"form-control", name:"parcel.depth"+num, min:"0.01", value: parcelDepth})))
            )
            .appendTo("#parcel${shipId}_" + num);
    }

    function deleteParcel${shipId}(index){
        /* elimino il collo */
        $("#parcel${shipId}_" + index).remove();

        /* aggiorno il numero dei colli */
        let num = parseInt($("#numberPackage${shipId}").val()) - 1;
        $("#numberPackage${shipId}").val(num);
        if(contatoreParcel == 0)
            contatoreParcel = num;
    }

function changeLocation() {
    var fedexApplication = document.getElementById("fedexApplication")
    if (fedexApplication.value === "MYRTL") {
        $("#national").show()
        $("#FEDEXShippingProductType_${shipId}").prop("required", "required")
			$("#FEDEXShippingIntProductType_${shipId}").prop("required", null)
			$("#international").hide()
		}
		else {
			$("#international").show()
			$("#FEDEXShippingIntProductType_${shipId}").prop("required", "required")
			$("#FEDEXShippingProductType_${shipId}").prop("required", null)
			$("#national").hide()
		}
	}
</script>

<div class="row">
    <div class="col-md-12">
        <div class="form-group">
            <label class="col-sm-3 control-label">
                Nazione destinatario
            </label>
            <div class="col-sm-9">
                <div class="input-group">
                    <select name="fedex_application" id="fedexApplication" class="form-control" onchange="changeLocation()">
                        <option value="MYRTL" ${shipping?.tntApplication == "MYRTL" ? "selected" : ""}>Italia</option>
                        <option value="MYRTLI" ${shipping?.tntApplication == "MYRTLI" ? "selected" : ""}>Europa</option>
                    </select>
                </div>
            </div>
        </div>
    </div>
</div>

<g:if test="${service == null || service?.isEmpty()}">
    <div class="row">
        <div class="col-md-12" id="national">
            <!-- Scelta del tipo di spedizione -->
            <div class="form-group">
                <label for="FEDEXShippingProductType_${shipId}" class="col-sm-3 control-label">
                    <g:message code="plugin.tntcarrier.manager.productType"/>
                </label>
                <div class="col-sm-9">
                    <div class="input-group">
                        <g:select class="form-control" name="nationalProductType" id="FEDEXShippingProductType_${shipId}" value="${shipping?.productType?.code}"
                                  from="${productTypes.findAll {it.location == FedexProductType.NATIONAL}}"
                                  optionValue="description" optionKey="code" required="required"/>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-12" id="international">
            <!-- Scelta del tipo di spedizione -->
            <div class="form-group">
                <label for="FEDEXShippingIntProductType_${shipId}" class="col-sm-3 control-label">
                    <g:message code="plugin.tntcarrier.manager.productType"/>
                </label>
                <div class="col-sm-9">
                    <div class="input-group">
                        <g:select class="form-control" name="internationalProductType" id="FEDEXShippingIntProductType_${shipId}" value="${shipping?.productType?.code}"
                                  from="${productTypes.findAll {it.location == FedexProductType.INTERNATIONAL}}"
                                  optionValue="description" optionKey="code" required="required"/>
                    </div>
                </div>
            </div>
        </div>
    </div>
</g:if>
<g:else>
    <input type="hidden" name="productType" value="${FedexProductType.findByCode(service)?.id}"/>
</g:else>

<!-- Inserimento dei colli della spedizione -->
<div class="row">
    <div class="col-md-12">
        <div class="form-group">
            <label class="col-sm-3 control-label">
                Numero di colli
            </label>
            <div class="col-sm-6">
                <div class="input-group">
                    <input class="form-control" type="number" min="1" id="numberPackage${shipId}" name="numberPackage" step="1" value="${shipping?.parcels?.size()?:1}" style="text-align: center;" readonly="">
                </div>
            </div>
            <div class="col-sm-3">
                <a class="btn btn-default btn-block" onclick="addParcel${shipId}()"><i class="fa fa-plus"></i></a>
            </div>
        </div>
    </div>
</div>

<!-- Colli della spedizione -->
<div class="row">
    <div class="col-md-12">
        <div id="packageform${shipId}">
            <g:each var="parcel" in="${shipping?.parcels ?: [new Parcel(width: parcelWidth, height: parcelHeight, depth: parcelDepth)]}" status="i">
                <div id="parcel${shipId}_${i}" class="row no-margins">
                    <input type="hidden" name="index" value="${i}">
                    <div class="col-sm-4">
                        <div class="form-group">
                            <label>Peso (in Kg)</label>
                            <div class="input-group">
                                <input type="number" class="form-control" name="parcel.weight${i}" min="0.01" value="${parcel.weight?:totalWeight}" style="text-align: center;" required="required">
                            </div>
                        </div>
                    </div>
                    <div class="col-sm-5 col-sm-offset-1">
                        <div class="form-group">
                            <label>Tipo collo</label>
                            <div class="input-group">
                                <g:select name="parcel.boxType${i}" from="${FedexBoxType.list()}" value="${parcel.boxType?.id}" required="required" class="form-control" optionValue="label" optionKey="id"/>
                            </div>
                        </div>
                    </div>
                    <div class="col-sm-2 m-t">
                        <a onclick="deleteParcel${shipId}(${i})"><i class="fa fa-trash fa-2x m-t"></i></a>
                    </div>
                    <div class="col-sm-12 dimensions">
                        <div class="form-group" style="margin-bottom: 0px">
                            <label>Dimensioni in centimetri</label>
                        </div>
                    </div>
                    <div class="col-sm-3 dimensions">
                        <div class="form-group">
                            <label>Larghezza</label>
                            <div class="input-group">
                                <input type="number" class="form-control" name="parcel.width${i}" min="0.01" value="${parcel.width ?: parcelWidth}">
                            </div>
                        </div>
                    </div>
                    <div class="col-sm-3 col-sm-offset-1 dimensions">
                        <div class="form-group">
                            <label>Altezza</label>
                            <div class="input-group">
                                <input type="number" class="form-control" name="parcel.height${i}" min="0.01" value="${parcel.height ?: parcelHeight}">
                            </div>
                        </div>
                    </div>
                    <div class="col-sm-3 col-sm-offset-1 dimensions">
                        <div class="form-group">
                            <label>Profondit&agrave;</label>
                            <div class="input-group">
                                <input type="number" class="form-control" name="parcel.depth${i}" min="0.01" value="${parcel.depth ?: parcelDepth}">
                            </div>
                        </div>
                    </div>
                </div>
            </g:each>
        </div>
    </div>
</div>

test value