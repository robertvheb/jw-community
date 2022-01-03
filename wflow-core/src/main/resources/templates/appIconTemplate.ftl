<div class="cards row">
    {{rows data-cbuilder-highlight="@@datalist.simpleCardTemplate.card@@" data-cbuilder-style="[{'prefix' : 'card', 'class' : '.appIcon', 'label' : '@@datalist.simpleCardTemplate.card@@'}]"}}
        <div class="col-12 {{columns_mobile}} {{columns_tablet}} {{columns_desktop}} p-2">
            {{selector}}
            <div class="appIcon" style="position:relative; text-align:center;">
                {{column_image data-cbuilder-droparea-msg="@@datalist.simpleCardTemplate.image@@" attr-class="card-img-top" attr-style="margin:0 auto 10px;"||<svg class="bd-placeholder-img" width="80px" height="80px" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMidYMid slice" focusable="false" role="img" aria-label="Placeholder: Image cap"><title>Placeholder</title><rect width="100%" height="100%" fill="#868e96"></rect><text x="50%" y="50%" fill="#dee2e6" dy=".3em">@@datalist.simpleCardTemplate.image@@</text></svg>}}
                {{column_title data-cbuilder-droparea-msg="@@datalist.simpleCardTemplate.title@@"}}
                    <h5 class="card-title">{{body||@@datalist.simpleCardTemplate.title@@}}</h5>
                {{column_title}}
                {{columns}}
                    {{column}}
                        <div class="card-text">
                            <#if element.properties.inlineLabel! == 'true'><strong class="label">{{label||@@datalist.simpleCardTemplate.label@@}}:</strong><br/></#if>
                            {{body||@@datalist.simpleCardTemplate.textContent@@}}
                        </div>
                    {{column}}
                {{columns}}
                {{rowAction_card data-cbuilder-name="@@datalist.simpleCardTemplate.cardAction@@" attr-class="stretched-link"}}
                {{rowActions data-cbuilder-sort-horizontal}}
                    <div class="card-actions">
                        {{rowAction}}
                    </div>
                {{rowActions}}
            </div>
        </div>
    {{rows}}
</div>
