<%@ page import="org.apache.commons.lang3.StringUtils; com.macrosolution.mpm.shipping.DealerShipping; com.macrosolution.mpm.supplier.Supplier" %>
<%@ page import = "com.macrosolution.mpm.shipper.ShipperType" %>
<%@ page import = "com.macrosolution.mpm.shipping.SpedisciOnlineShipping" %>
<script type="text/javascript">
	function getShippingForm(shipperType, shipping_id, orderId){
    	$.ajax({
			method:'POST',
    		url: '${createLink(controller:'shipping', action: 'getForm')}',
    		data: {"id":shipperType, "shipping_id":shipping_id, "order_id": orderId},
    		beforeSend: function(){
    		},
    		error: function(xhr,status,error){swal("${g.message(code:'default.error')}",xhr.responseText, "error");
    		},
    		success:function(data){
    			$("#target${shipping.id}").html(data)
    		},
		})
	}

	/**
	 * Funzione per la modifica dei dati di una spedizione
	 * */
	function editShipping(btn, id){
		let form = $("#formShipping_"+id);
		if (form.valid()){
			$.ajax({
				url: '${createLink(controller:'shipping', action:'update')}',
				type:"POST",
				data: form.serialize(),
				beforeSend: function() {
					$(btn).ladda().ladda('start')
				},
				error: function(xhr){
					if(xhr.status == 401){
						swal("Attenzione",xhr.responseText, "info");
					}else{
						swal("${g.message(code:'default.error')}",xhr.responseText, "error");
					}
				},
				success:function(){
					<!-- Informo l'utente di aver aggiornato i dati della spedizione -->
					toastr.success("Spedizione aggiornata", "${g.message(code:'default.success')}");

					<!-- mostro il modal per la ristampa dell'etichetta -->
					<g:if test="${fastActions?.confirmShippingSaveEdit==true}">
					$("#printlabelModal").modal("show");
					</g:if>
				},
				complete:function(){
					$(btn).ladda('stop');
				},
			});
		}
	}

	function goToDealerRemoteDetail(btn, delaerType, shippingId, linkType) {
		$.ajax({
			url: '${createLink(controller: 'dealer', action: 'shippingRemoteDetail')}',
			type: "GET",
			data: {
				shippingId: shippingId,
				delaerType: delaerType,
				linkType: linkType,
			},
			beforeSend: function () {
				$(btn).ladda().ladda('start');
			},
			success: function (response) {
				$(btn).ladda('stop');

				console.log(response)
				if(response.indexOf("NO_URL") > -1) {
					swal({
						type: "warning",
						title: "${g.message(code: 'commons.attention')}",
						text: "${g.message(code: 'shippings.dealer.actions.confirmRemote.noUrl')}"
					})
				}
				else {
					window.open(response, '_blank');
				}

			},
			error: function (xhr, status, error) {
				$(btn).ladda('stop');
				swal("${g.message(code:'default.error')}", xhr.responseText, "error");
			}
		});
	}

	<g:if test="${orderId != null}">
	function quickDownloadLabel(btn, id) {
		$.ajax({
			url: '${createLink(controller:'shipping',action:'downloadLabel', id:shipping.id)}',
			type: "GET",
			beforeSend: function () {
				$(btn).ladda().ladda('start');
			},
			success: function (response) {
				$(btn).ladda('stop');
				downloadBase64AsFile(response.data, response.mime, response.name);
				refreshOrderShipping(${orderId}, null);
				refreshDealerBox(${order.id});


			},
			error: function (xhr, status, error) {
				$(btn).ladda('stop');
				swal("${g.message(code:'default.error')}", xhr.responseText, "error");
			}
		});
	}

	/**
	 * Funzione per stampare velocemente l'etichetta nel dettaglio dell'ordine
	 * */
	function quickPrintLabel(btn, id){
    	$.ajax({
    		url: '${createLink(controller:'shipping',action:'setStatus')}',
    		type:"POST",
    		data:{id:id, complete:true},
			beforeSend: function() {
				$(btn).ladda().ladda('start')
			},
    		error: function(xhr,status,error){
    			swal("${g.message(code:'default.error')}",xhr.responseText, "error");
    		},
    		success:function(data){
	        	var text='';
	        	if('${shipping.shipper.type}'=='TNT'){
	        		text='<p>Questa spedizione è stata trasmessa alla sede di riferimento del corriere!<p>'
	        	} else {
	        		if(${shipping.confirmed?:false}){
	        			text="<p>Vuoi stampare di nuovo l'etichetta per questa spedizione?<p>"
	        		}else{
	        			text='<p>Ricordati di confermare la spedizione per trasmetterla alla sede di riferimento del corriere!</p>'
	        		}
	        	}
				if('${shipping.shipper.type}' === 'GLS' ) {
					text += '<p><b>Ricordati di controllare la lettera di vettura per eventuali errori ricevuti dal corriere!</b></p>'
				}

				<g:if test="${fastActions?.confirmOrderPrintLabel}">
				swal({
				  	title: "Crea etichetta!",
				  	text: text,
					html: true,
				  	type: "info",
				  	showCancelButton: true,
				  	closeOnConfirm: false,
				  	showLoaderOnConfirm: true,
				},function(){
					</g:if>
					$.ajax({
						method: "GET",
						url: "${createLink(controller:'shipping',action:'printLabel', id:shipping.id)}",
			            success: function (response) {
							if(response.data != null) {
								downloadBase64AsFile(response.data, response.mime, response.name);
							}
							var successText = ""
							<g:if test="${fastActions?.confirmOrderPrintLabel}">
								if(response == '${SpedisciOnlineShipping.CREDITO_ESAURITO_CODE}') {
									swal({
										title: "Attenzione!",
										text: '${SpedisciOnlineShipping.CREDITO_ESAURITO_MESSAGE}',
										type: "warning",
										showCancelButton: false,
										closeOnConfirm: false
									},
									function(){
										window.location.reload();
									});
								}
								else {
									successText = "Download dell'etichetta effettuato con successo!"
									swal({
											title: "Fatto!",
											text: successText,
											type: "success",
											showCancelButton: false,
											closeOnConfirm: true

										},
										function(){
											refreshOrderShipping(${orderId}, null);
											refreshDealerBox(${order.id});

										}
									);
								}
				            </g:if>
							<g:else>
								if(response == '${SpedisciOnlineShipping.CREDITO_ESAURITO_CODE}') {
									toastr.warning('${SpedisciOnlineShipping.CREDITO_ESAURITO_MESSAGE}', "Attenzione");
								}
								else{
									successText = "Stampa dell'etichetta effettuata con successo, il download inizierà a breve"
									toastr.success(successText);
								}
								refreshOrderShipping(${orderId}, null);
								refreshDealerBox(${order.id});

							</g:else>

							refreshOrderShipping(${orderId}, null);
							refreshDealerBox(${order.id});
							<poleepo:infoActionMessage	hasClaim="INFO_ACTION_MESSAGE" message="${g.message(code:"info.orderPrintLabel")}" action="orderPrintLabel">
							window.location.reload();
							</poleepo:infoActionMessage>

				       	},
			            error: function (xhr,status,error) {
							refreshOrderShipping(${orderId}, null);
							refreshDealerBox(${order.id});
			 				swal({
							    title: 'Ops!',
							    text: xhr.responseText,
							    html: true,
							    type: "error"
						    });
			            }
			        });
				<g:if test="${fastActions?.confirmOrderPrintLabel==true}">
		        });
	        	</g:if>
    		},
    		complete: function(xhr,status,error){
    			$(btn).ladda('stop');
    		}
    	});
	}
	</g:if>
</script>

<form class="form-horizontal" id="formShipping_${shipping.id}">
	<!-- Titolo e stato della spedizione -->
	<div class="row m-b">
		<div class="col-md-12">
			<!-- Nome del corriere -->
			<cdn:img src="img/shipper/logo/${shipping.shipper.type.type?.toLowerCase()}.png" width="20" height="20"/>
			<span class="m-l-xs">
				${shipping.shipper.title}
			</span>
		</div>
	</div>
	<div class="row m-b">
		<div class="col-md-7">

			<!-- Stato della spedizione -->
			<tag_shipping:status shipping="${shipping}"/>
%{--			<g:if test="${shipping.customerDeliveryDate}">--}%
%{--				<span class="label label-success">Consegnata</span>--}%
%{--			</g:if>--}%
%{--			<g:elseif test="${shipping.dateRejected}">--}%
%{--				<span class="label label-danger">Annullata</span>--}%
%{--			</g:elseif>--}%
%{--			<g:elseif test="${shipping.sended}">--}%
%{--				<span class="label label-success">Spedita</span>--}%
%{--			</g:elseif>--}%
%{--			<g:elseif test="${shipping.confirmed && shipping.dropShipperConfirmed}">--}%
%{--				<span class="label label-warning">Stampata, accettata e confermata</span>--}%
%{--			</g:elseif>--}%
%{--			<g:elseif test="${shipping.confirmed}">--}%
%{--				<span class="label label-warning">Stampata e confermata</span>--}%
%{--			</g:elseif>--}%
%{--			<g:elseif test="${shipping.printed && shipping.dropShipperConfirmed}">--}%
%{--				<span class="label label-danger">Stampata, accettata da confermare</span>--}%
%{--			</g:elseif>--}%
%{--			<g:elseif test="${shipping.printed}">--}%
%{--				<span class="label label-danger">Stampata da confermare</span>--}%
%{--			</g:elseif>--}%
%{--			<g:elseif test="${shipping.complete}">--}%
%{--				<span class="label label-primary">Pronta</span>--}%
%{--			</g:elseif>--}%
%{--			<g:else>--}%
%{--				<span class="label label-plain">Da preparare</span>--}%
%{--			</g:else>--}%
		</div>

		<%-- Info per riconoscere una spedizione di un dropShipper interno --%>
%{--		<g:if test="${Supplier.findByAddress(shipping.pickupAddress)?.dealerType != null}">--}%
			<div class="col-md-5 text-right">
				<span class="label label-primary">${shipping.pickupAddress?.label}</span>
			</div>
%{--		</g:if>--}%
	</div>

	<hr />

	<!-- Form del corriere -->
	<div class="row m-b">
		<div class="col-md-12">
			<input type="hidden" name="id" value="${shipping.id}"/>

			<g:if test="${shipping.shipper instanceof com.macrosolution.mpm.shipper.GlsShipper}">
				<!-- se è una spedizione di GLS, carico il form di GLS -->
				<g:render template="/shipping/shippingGlsForm" model="['shipping':shipping]"/>
			</g:if>
			<g:elseif test="${shipping.shipper.type.type == com.macrosolution.mpm.shipper.ShipperType.TNT}">
				<!-- se è una spedizione di Tnt, carico il form di TNT -->
%{--				<g:render template="/shipping/shippingTntForm" model="['shipping':shipping, productTypes: productTypes]"/>--}%
				<!-- se è una spedizione di TNT, carico il form di TNT -->
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}", "${orderId}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>
			<g:elseif test="${shipping.shipper.type.type == 'SDA' || shipping.shipper.type.type == 'POSTE' || shipping.shipper.type.type == 'POSTE_DELIVERY'}">
				<!-- se è una spedizione di SDA, carico il form di SDA -->
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}", "${orderId}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>
			<g:elseif test="${shipping.shipper.type.type == 'BRT'}">
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}", "${orderId}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>
			<g:elseif test="${shipping.shipper.type.type == 'DHL'}">
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}", "${orderId}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>
			<g:elseif test="${shipping.shipper.type.type == ShipperType.SPEDISCI_ONLINE}">
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}", "${orderId}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>
			<g:elseif test="${shipping.shipper.type.type == ShipperType.MIT}">
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}", "${orderId}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>
			<g:elseif test="${shipping.shipper.type.type == ShipperType.QAPLA}">
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>

			<g:elseif test="${shipping.shipper.type.type == 'FEDEX'}">
				<script>getShippingForm("${shipping.shipper.virtualShipperType}", "${shipping.id}", "${orderId}");</script>
				<div id="target${shipping.id}"></div>
			</g:elseif>

			<g:elseif test="${shipping.shipper.type.type == ShipperType.AMAZON_IT ||
					shipping.shipper.type.type == ShipperType.AMAZON_DE ||
					shipping.shipper.type.type == ShipperType.AMAZON_ES ||
					shipping.shipper.type.type == ShipperType.AMAZON_FR ||
					shipping.shipper.type.type == ShipperType.AMAZON_GB}">
				<g:render template="/shipping/mpShipping/form/amazon" model="['shipping': shipping]"/>
			</g:elseif>
			<g:elseif test="${shipping instanceof DealerShipping}">
				<g:render template="/shipping/dealerShippings/dealerShipping" model="[shipping: shipping]" />
			</g:elseif>
			<g:else>
				<!-- se è una spedizione di Generica, carico il form Generico -->
				<g:render template="/shipping/shippingGenericForm" model="['shipping':shipping]"/>
			</g:else>
		</div>
	</div>

	<!-- Azioni sulla spedizione -->
	<div class="modal-footer">
		<div class="row">
			<div class="col-md-12 text-right">
				<g:if test="${orderId !=  null}">
					<!-- Dettaglio della spedizione -->
					<p>
						<g:if test="${shipping instanceof DealerShipping}">
							<g:if test="${StringUtils.isBlank(shipping?.shippingCode)}">
								<a class="btn btn-warning ladda-button" data-style="expand-left" onclick="goToDealerRemoteDetail(this, '${(shipping as DealerShipping).dealerType}', ${shipping.id}, 1)">
									<g:message code="shippings.dealer.actions.confirmRemote" args="[(shipping as DealerShipping).shipper.title]"/>
								</a>
							</g:if>
							<g:else>
								<a class="btn btn-success ladda-button" data-style="expand-left" onclick="goToDealerRemoteDetail(this, '${(shipping as DealerShipping).dealerType}', ${shipping.id}, 2)">
									<g:message code="shippings.dealer.actions.remoteDetails" args="[(shipping as DealerShipping).shipper.title]"/>
								</a>
							</g:else>
						</g:if>
						<a class="btn btn-success ladda-button" href="${createLink(controller:'shipping', action:'edit', id: shipping.id)}">
							Vai al dettaglio
						</a>
						<g:if test="${shipping.fileLabel != null}">
							<a class="btn btn-success ladda-button" data-style="expand-left" onclick="quickDownloadLabel(this, ${shipping.id})" data-toggle="tooltip" title="Scarica l'etichetta già creata in precedenza">
								<i class="fa fa-download"></i> <g:message code="default.button.download.label"/>
							</a>

						</g:if>
						<!-- Stampa rapida dell'etichetta -->
						<g:if test="${!shipping.sended && shipping.shipper.type.type!=ShipperType.GEN && !shipping.toDownload && !(shipping instanceof DealerShipping)}">
							<a class="btn btn-success ladda-button" data-style="expand-left" onclick="quickPrintLabel(this, ${shipping.id})">
								${shipping.printed ? 'Ricrea' : 'Crea'} etichetta
							</a>
						</g:if>
						<g:elseif test="${shipping.shipper.type.type!=ShipperType.GEN && !(shipping instanceof DealerShipping)}">
							<g:if test="${!shipping.shipper.type.isMarketplace()}">
							<br/>
							<br/>
							</g:if>
							<g:if test="${!shipping.shipper.type.isMarketplace()}">
							<a class="btn btn-success ladda-button" data-style="expand-left" onclick="quickPrintLabel(this, ${shipping.id})">
								Ricrea etichetta
							</a>
							</g:if>
						</g:elseif>
					</p>
				</g:if>
			</div>
		</div>
		<div class="row">
			<div class="col-md-12 text-right">
				<!-- Salvataggio delle modifiche -->
				<g:if test="${!shipping.shipper.type.isMarketplace() && !(shipping instanceof DealerShipping)}">
				<a id="btnEditShipping_${shipping.id}" class="btn btn-success ladda-button" data-style="expand-left" onclick="editShipping(this, ${shipping.id})">
					Salva le modifiche
				</a>
				</g:if>

				<!-- Eliminazione della spedizione -->
				<g:if test="${orderId !=  null && !(shipping instanceof DealerShipping)}">
					<a id="btnDeleteShipping_${shipping.id}" class="btn btn-warning ladda-button" data-style="expand-left" onclick="deleteShipping(this,'${shipping.id}')">
						<g:message code="default.button.delete.label" />
					</a>
				</g:if>
			</div>
		</div>
	</div>
</form>

<!-- Modal per la ristampa dell'etichetta in caso di modifica della spedizione -->
<div class="modal fade" id="printlabelModal" tabindex="-1" role="dialog" aria-labelledby="exampleModalLabel" aria-hidden="true">
	<div class="modal-dialog" role="document">
		<div class="modal-content">
			<div class="modal-header">
				<h5 class="modal-title" id="printlabelModalLabel">Attenzione</h5>
				<button type="button" class="close" data-dismiss="modal" aria-label="Close">
					<span aria-hidden="true">&times;</span>
				</button>
			</div>
			<div class="modal-body">
				<h3>Devi ricreare l'etichetta per aggiornare i dati del corriere perchè hai modificato i dati della spedizione.</h3>
			</div>
			<div class="modal-footer">
				<button type="button" class="btn btn-secondary" data-dismiss="modal">Chiudi</button>

			</div>
		</div>
	</div>
</div>
