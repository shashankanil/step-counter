CREATE TABLE "friendship" (
	"id" text PRIMARY KEY NOT NULL,
	"requester" text NOT NULL,
	"recipient" text NOT NULL,
	"status" text DEFAULT 'pending' NOT NULL,
	CONSTRAINT "friendship_not_self" CHECK ("friendship"."requester" <> "friendship"."recipient"),
	CONSTRAINT "friendship_status" CHECK ("friendship"."status" in ('pending', 'accepted'))
);
--> statement-breakpoint
CREATE TABLE "group_member" (
	"group_id" text NOT NULL,
	"user_id" text NOT NULL,
	CONSTRAINT "group_member_group_id_user_id_pk" PRIMARY KEY("group_id","user_id")
);
--> statement-breakpoint
CREATE TABLE "shared_steps" (
	"user_id" text NOT NULL,
	"date" date NOT NULL,
	"steps" integer NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "shared_steps_user_id_date_pk" PRIMARY KEY("user_id","date"),
	CONSTRAINT "non_negative_steps" CHECK ("shared_steps"."steps" >= 0)
);
--> statement-breakpoint
CREATE TABLE "step_group" (
	"id" text PRIMARY KEY NOT NULL,
	"name" text NOT NULL,
	"code" text NOT NULL,
	CONSTRAINT "step_group_code_unique" UNIQUE("code")
);
--> statement-breakpoint
CREATE TABLE "step_sharing" (
	"user_id" text PRIMARY KEY NOT NULL,
	"enabled" boolean DEFAULT false NOT NULL
);
--> statement-breakpoint
ALTER TABLE "friendship" ADD CONSTRAINT "friendship_requester_user_id_fk" FOREIGN KEY ("requester") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "friendship" ADD CONSTRAINT "friendship_recipient_user_id_fk" FOREIGN KEY ("recipient") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "group_member" ADD CONSTRAINT "group_member_group_id_step_group_id_fk" FOREIGN KEY ("group_id") REFERENCES "public"."step_group"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "group_member" ADD CONSTRAINT "group_member_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "shared_steps" ADD CONSTRAINT "shared_steps_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "step_sharing" ADD CONSTRAINT "step_sharing_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "friendship_pair" ON "friendship" USING btree (least("requester", "recipient"),greatest("requester", "recipient"));